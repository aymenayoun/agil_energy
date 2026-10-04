package com.agil.energy.service;

import com.agil.energy.entity.BlacklistedToken;
import com.agil.energy.repository.BlacklistedTokenRepository;
import com.agil.energy.service.impl.TokenBlacklistServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceImplTest {

    @Mock private BlacklistedTokenRepository repo;
    @InjectMocks private TokenBlacklistServiceImpl service;

    @Test
    @DisplayName("blacklist — saves entity for valid inputs")
    void blacklist_validInputs_saves() {
        when(repo.existsByJti("jti-1")).thenReturn(false);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        service.blacklist("jti-1", 42L, expiresAt, "logout");

        verify(repo).save(any(BlacklistedToken.class));
    }

    @Test
    @DisplayName("blacklist — null jti is no-op")
    void blacklist_nullJti_doesNothing() {
        service.blacklist(null, 1L, LocalDateTime.now().plusHours(1), "logout");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("blacklist — blank jti is no-op")
    void blacklist_blankJti_doesNothing() {
        service.blacklist("   ", 1L, LocalDateTime.now().plusHours(1), "logout");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("blacklist — null expiresAt is no-op")
    void blacklist_nullExpiresAt_doesNothing() {
        service.blacklist("jti-1", 1L, null, "logout");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("blacklist — duplicate jti is not saved twice")
    void blacklist_duplicate_skipsSave() {
        when(repo.existsByJti("jti-1")).thenReturn(true);
        service.blacklist("jti-1", 1L, LocalDateTime.now().plusHours(1), "logout");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("isBlacklisted — returns true when present")
    void isBlacklisted_present_returnsTrue() {
        when(repo.existsByJti("jti-1")).thenReturn(true);
        assertThat(service.isBlacklisted("jti-1")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted — null returns false without DB hit")
    void isBlacklisted_null_returnsFalse() {
        assertThat(service.isBlacklisted(null)).isFalse();
        verify(repo, never()).existsByJti(any());
    }

    @Test
    @DisplayName("isBlacklisted — blank returns false without DB hit")
    void isBlacklisted_blank_returnsFalse() {
        assertThat(service.isBlacklisted("  ")).isFalse();
        verify(repo, never()).existsByJti(any());
    }

    @Test
    @DisplayName("cleanupExpired — invokes repository delete")
    void cleanupExpired_callsRepo() {
        when(repo.deleteExpired(any())).thenReturn(3);
        service.cleanupExpired();
        verify(repo).deleteExpired(any());
    }
}