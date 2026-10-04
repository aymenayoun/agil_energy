package com.agil.energy.service;

import com.agil.energy.entity.PasswordResetToken;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.PasswordResetTokenRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.service.impl.PasswordResetServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock private PasswordResetTokenRepository resetRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;

    @InjectMocks private PasswordResetServiceImpl service;

    private User user;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expirationMinutes", 30);
        ReflectionTestUtils.setField(service, "resetUrlBase", "http://localhost:4200/reset-password");

        user = new User();
        user.setId(1L);
        user.setEmail("admin@agil.tn");
        user.setName("Admin User");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("current-hash");

        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("10.0.0.5");
    }

    // ── requestReset (anti-enumeration) ───────────────────────

    @Test
    @DisplayName("requestReset — unknown email does nothing (no token, no email)")
    void requestReset_unknownEmail_noop() {
        when(userRepository.findByEmail("ghost@agil.tn")).thenReturn(Optional.empty());

        service.requestReset("ghost@agil.tn", httpRequest);

        verify(resetRepository, never()).save(any());
        verify(emailService, never()).sendHtml(any(), any(), any(), anyMap());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("requestReset — inactive account does nothing")
    void requestReset_inactiveAccount_noop() {
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmail("admin@agil.tn")).thenReturn(Optional.of(user));

        service.requestReset("admin@agil.tn", httpRequest);

        verify(resetRepository, never()).save(any());
        verify(emailService, never()).sendHtml(any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("requestReset — null email is handled safely")
    void requestReset_nullEmail_noop() {
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        service.requestReset(null, httpRequest);

        verify(resetRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestReset — active user: invalidates old tokens, saves new, emails link, audits")
    void requestReset_activeUser_issuesToken() {
        when(userRepository.findByEmail("admin@agil.tn")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.requestReset("admin@agil.tn", httpRequest);

        verify(resetRepository).invalidateAllForUser(1L);
        verify(resetRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendHtml(eq("admin@agil.tn"), anyString(), eq("reset-password-email"), anyMap());
        verify(auditService).log(eq(1L), eq("PASSWORD_RESET_REQUESTED"), eq("User"), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("requestReset — stored token never contains the raw verifier (only its hash)")
    void requestReset_storesHashNotRawVerifier() {
        when(userRepository.findByEmail("admin@agil.tn")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.requestReset("admin@agil.tn", httpRequest);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isEqualTo("verifier-hash");
        assertThat(saved.getSelector()).isNotBlank();
        assertThat(saved.getUsed()).isFalse();
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // ── confirmReset (validation paths) ───────────────────────

    @Test
    @DisplayName("confirmReset — null token throws")
    void confirmReset_nullToken_throws() {
        assertThatThrownBy(() -> service.confirmReset(null, "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("confirmReset — token with no dot separator throws")
    void confirmReset_malformedToken_throws() {
        assertThatThrownBy(() -> service.confirmReset("no-separator", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
        verify(resetRepository, never()).findBySelector(any());
    }

    @Test
    @DisplayName("confirmReset — unknown selector throws")
    void confirmReset_unknownSelector_throws() {
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("confirmReset — already used token throws")
    void confirmReset_alreadyUsed_throws() {
        PasswordResetToken prt = token().used(true).build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà");
    }

    @Test
    @DisplayName("confirmReset — expired token throws")
    void confirmReset_expired_throws() {
        PasswordResetToken prt = token().expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("confirmReset — too many attempts burns the token and throws")
    void confirmReset_tooManyAttempts_throws() {
        PasswordResetToken prt = token().attempts(5).build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tentatives");

        assertThat(prt.getUsed()).isTrue();
        verify(resetRepository).save(prt);
    }

    @Test
    @DisplayName("confirmReset — wrong verifier increments attempts and throws")
    void confirmReset_wrongVerifier_incrementsAttempts() {
        PasswordResetToken prt = token().build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));
        when(passwordEncoder.matches("verifier", "verifier-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class);

        assertThat(prt.getAttempts()).isEqualTo(1);
        verify(resetRepository).save(prt);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmReset — reusing the current password is rejected")
    void confirmReset_reusedPassword_throws() {
        PasswordResetToken prt = token().build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));
        when(passwordEncoder.matches("verifier", "verifier-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.confirmReset("sel.verifier", "NewPass123", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("différent");

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("confirmReset — valid token sets new hash, consumes token, revokes sessions, audits")
    void confirmReset_valid_resetsPassword() {
        PasswordResetToken prt = token().build();
        when(resetRepository.findBySelector("sel")).thenReturn(Optional.of(prt));
        when(passwordEncoder.matches("verifier", "verifier-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        service.confirmReset("sel.verifier", "NewPass123", httpRequest);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(prt.getUsed()).isTrue();
        verify(userRepository).save(user);
        verify(resetRepository).save(prt);
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(auditService).log(eq(1L), eq("PASSWORD_RESET_COMPLETED"), eq("User"), eq(1L), anyString(), anyString());
        verify(emailService).sendHtml(eq("admin@agil.tn"), anyString(), eq("password-changed-email"), anyMap());
    }

    // ── cleanup ───────────────────────────────────────────────

    @Test
    @DisplayName("cleanup — calls repository delete")
    void cleanup_deletesExpired() {
        when(resetRepository.deleteExpiredOrUsed(any())).thenReturn(3);
        service.cleanup();
        verify(resetRepository).deleteExpiredOrUsed(any());
    }

    // ── helper ────────────────────────────────────────────────

    /** A valid, unused, non-expired token owned by {@code user}, verifier hash = "verifier-hash". */
    private PasswordResetToken.PasswordResetTokenBuilder token() {
        return PasswordResetToken.builder()
                .selector("sel")
                .user(user)
                .tokenHash("verifier-hash")
                .used(false)
                .attempts(0)
                .expiresAt(LocalDateTime.now().plusMinutes(30));
    }
}
