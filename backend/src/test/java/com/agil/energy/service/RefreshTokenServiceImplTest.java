package com.agil.energy.service;

import com.agil.energy.entity.RefreshToken;
import com.agil.energy.entity.User;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.RefreshTokenRepository;
import com.agil.energy.service.impl.RefreshTokenServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock private RefreshTokenRepository repo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private HttpServletRequest request;

    @InjectMocks private RefreshTokenServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 7L * 24 * 60 * 60 * 1000);
        user = new User(); user.setId(1L); user.setEmail("u@x.com");
    }

    // ── generate ──────────────────────────────────────────────

    @Test
    @DisplayName("generate — saves token entity and returns 'tokenId.secret' format")
    void generate_savesAndReturnsCompositeToken() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String token = service.generate(user, request);

        assertThat(token).contains(".");
        assertThat(token.split("\\.")).hasSize(2);
        verify(repo).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("generate — uses X-Forwarded-For when present")
    void generate_usesForwardedFor() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(request.getHeader("User-Agent")).thenReturn("Agent");
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1, 10.0.0.1");

        service.generate(user, request);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("generate — null request still works")
    void generate_nullRequest_works() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        String token = service.generate(user, null);

        assertThat(token).contains(".");
        verify(repo).save(any(RefreshToken.class));
    }

    // ── validateAndGetUser ────────────────────────────────────

    @Test
    @DisplayName("validateAndGetUser — null token throws")
    void validate_null_throws() {
        assertThatThrownBy(() -> service.validateAndGetUser(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateAndGetUser — token without separator throws")
    void validate_noSeparator_throws() {
        assertThatThrownBy(() -> service.validateAndGetUser("nodot"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateAndGetUser — empty secret throws")
    void validate_emptySecret_throws() {
        assertThatThrownBy(() -> service.validateAndGetUser("tokenId."))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateAndGetUser — unknown tokenId throws")
    void validate_unknownToken_throws() {
        when(repo.findByTokenId("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndGetUser("abc.secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("validateAndGetUser — revoked token throws")
    void validate_revoked_throws() {
        RefreshToken stored = RefreshToken.builder()
                .tokenId("abc").user(user).tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(true).build();
        when(repo.findByTokenId("abc")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validateAndGetUser("abc.secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("révoqué");
    }

    @Test
    @DisplayName("validateAndGetUser — expired token throws")
    void validate_expired_throws() {
        RefreshToken stored = RefreshToken.builder()
                .tokenId("abc").user(user).tokenHash("h")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false).build();
        when(repo.findByTokenId("abc")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.validateAndGetUser("abc.secret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("validateAndGetUser — wrong secret throws")
    void validate_wrongSecret_throws() {
        RefreshToken stored = RefreshToken.builder()
                .tokenId("abc").user(user).tokenHash("hashed")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false).build();
        when(repo.findByTokenId("abc")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.validateAndGetUser("abc.wrong"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("validateAndGetUser — valid token returns user and updates lastUsedAt")
    void validate_valid_returnsUser() {
        RefreshToken stored = RefreshToken.builder()
                .tokenId("abc").user(user).tokenHash("hashed")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false).build();
        when(repo.findByTokenId("abc")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("ok", "hashed")).thenReturn(true);

        User result = service.validateAndGetUser("abc.ok");

        assertThat(result).isEqualTo(user);
        assertThat(stored.getLastUsedAt()).isNotNull();
        verify(repo).save(stored);
    }

    // ── revoke ────────────────────────────────────────────────

    @Test
    @DisplayName("revoke — marks token as revoked")
    void revoke_marksRevoked() {
        RefreshToken stored = RefreshToken.builder()
                .tokenId("abc").user(user).tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revoked(false).build();
        when(repo.findByTokenId("abc")).thenReturn(Optional.of(stored));

        service.revoke("abc.secret");

        assertThat(stored.getRevoked()).isTrue();
        verify(repo).save(stored);
    }

    @Test
    @DisplayName("revoke — null/blank/no-separator is silent no-op")
    void revoke_invalidInputs_noOp() {
        service.revoke(null);
        service.revoke("nodot");

        verify(repo, never()).save(any());
    }

    // ── revokeAllForUser ──────────────────────────────────────

    @Test
    @DisplayName("revokeAllForUser — calls repository")
    void revokeAllForUser_callsRepo() {
        when(repo.revokeAllForUser(1L)).thenReturn(3);
        service.revokeAllForUser(1L);
        verify(repo).revokeAllForUser(1L);
    }

    @Test
    @DisplayName("getRefreshExpirationMs — returns configured")
    void getRefreshExpirationMs_returnsConfigured() {
        assertThat(service.getRefreshExpirationMs()).isEqualTo(7L * 24 * 60 * 60 * 1000);
    }

    @Test
    @DisplayName("cleanupExpired — calls repository delete")
    void cleanupExpired_callsRepo() {
        when(repo.deleteExpiredOrRevoked(any())).thenReturn(5);
        service.cleanupExpired();
        verify(repo).deleteExpiredOrRevoked(any());
    }
}