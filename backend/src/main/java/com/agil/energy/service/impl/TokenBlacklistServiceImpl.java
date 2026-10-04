package com.agil.energy.service.impl;

import com.agil.energy.entity.BlacklistedToken;
import com.agil.energy.repository.BlacklistedTokenRepository;
import com.agil.energy.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    @Transactional
    public void blacklist(String jti, Long userId, LocalDateTime expiresAt, String reason) {
        if (jti == null || jti.isBlank() || expiresAt == null) return;
        if (blacklistedTokenRepository.existsByJti(jti)) return;

        BlacklistedToken entry = BlacklistedToken.builder()
                .jti(jti)
                .userId(userId)
                .expiresAt(expiresAt)
                .revokedAt(LocalDateTime.now())
                .reason(reason)
                .build();
        blacklistedTokenRepository.save(entry);
        log.debug("JWT blacklisté : jti={}, user={}, raison={}", jti, userId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return blacklistedTokenRepository.existsByJti(jti);
    }

    /** Daily cleanup of blacklist entries whose underlying token has already expired. */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpired() {
        int n = blacklistedTokenRepository.deleteExpired(LocalDateTime.now());
        if (n > 0) log.info("Nettoyage blacklisted_tokens : {} entrées supprimées", n);
    }
}