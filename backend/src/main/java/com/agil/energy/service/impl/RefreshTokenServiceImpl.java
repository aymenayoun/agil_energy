package com.agil.energy.service.impl;

import com.agil.energy.entity.RefreshToken;
import com.agil.energy.entity.User;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.RefreshTokenRepository;
import com.agil.energy.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** Format of the refresh token sent to the client: "<tokenId>.<secret>" */
    private static final String SEPARATOR = ".";
    private static final SecureRandom RNG = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Override
    @Transactional
    public String generate(User user, HttpServletRequest request) {
        String tokenId = UUID.randomUUID().toString();
        String secret = generateSecret();
        String tokenHash = passwordEncoder.encode(secret);

        RefreshToken token = RefreshToken.builder()
                .tokenId(tokenId)
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)))
                .revoked(false)
                .userAgent(truncate(getHeader(request, "User-Agent"), 255))
                .ipAddress(extractClientIp(request))
                .build();

        refreshTokenRepository.save(token);
        return tokenId + SEPARATOR + secret;
    }

    @Override
    @Transactional
    public User validateAndGetUser(String rawToken) {
        Parsed parsed = parse(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenId(parsed.tokenId)
                .orElseThrow(() -> new BusinessException("Refresh token invalide"));

        if (Boolean.TRUE.equals(stored.getRevoked())) {
            throw new BusinessException("Refresh token révoqué");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token expiré");
        }
        if (!passwordEncoder.matches(parsed.secret, stored.getTokenHash())) {
            throw new BusinessException("Refresh token invalide");
        }

        stored.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);
        return stored.getUser();
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || !rawToken.contains(SEPARATOR)) return;
        String tokenId = rawToken.substring(0, rawToken.indexOf(SEPARATOR));
        refreshTokenRepository.findByTokenId(tokenId).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        int n = refreshTokenRepository.revokeAllForUser(userId);
        log.info("Refresh tokens révoqués pour l'utilisateur {} : {}", userId, n);
    }

    @Override
    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    /** Daily cleanup of expired or revoked refresh tokens. */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpired() {
        int n = refreshTokenRepository.deleteExpiredOrRevoked(LocalDateTime.now());
        if (n > 0) log.info("Nettoyage refresh_tokens : {} entrées supprimées", n);
    }

    // --- helpers ---

    private record Parsed(String tokenId, String secret) {}

    private static Parsed parse(String rawToken) {
        if (rawToken == null) throw new BusinessException("Refresh token invalide");
        int idx = rawToken.indexOf(SEPARATOR);
        if (idx <= 0 || idx == rawToken.length() - 1) {
            throw new BusinessException("Refresh token invalide");
        }
        return new Parsed(rawToken.substring(0, idx), rawToken.substring(idx + 1));
    }

    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String getHeader(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}