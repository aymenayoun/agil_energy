package com.agil.energy.service.impl;

import com.agil.energy.entity.PasswordResetToken;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.PasswordResetTokenRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.EmailService;
import com.agil.energy.service.PasswordResetService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final int MAX_ATTEMPTS = 5;
    private static final int SELECTOR_BYTES = 12;   // public lookup key
    private static final int VERIFIER_BYTES = 32;   // 256-bit secret

    private final PasswordResetTokenRepository resetRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Value("${app.reset.expiration-minutes:30}")
    private int expirationMinutes;

    @Value("${app.reset.url-base:http://localhost:4200/reset-password}")
    private String resetUrlBase;

    // ───────────────────────── Step 1: request ─────────────────────────

    @Override
    @Transactional
    public void requestReset(String email, HttpServletRequest request) {
        Optional<User> maybeUser = userRepository.findByEmail(email == null ? "" : email.trim());

        // Anti-enumeration: behave identically whether or not the user exists.
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for unknown email (ignored): {}", mask(email));
            return;
        }

        User user = maybeUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Password reset requested for inactive account (ignored): {}", mask(email));
            return;
        }

        // Only one active token per user: invalidate any previous ones first.
        resetRepository.invalidateAllForUser(user.getId());

        String selector = B64.encodeToString(randomBytes(SELECTOR_BYTES));
        String verifier = B64.encodeToString(randomBytes(VERIFIER_BYTES));

        PasswordResetToken token = PasswordResetToken.builder()
                .selector(selector)
                .user(user)
                .tokenHash(passwordEncoder.encode(verifier))
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .used(false)
                .attempts(0)
                .build();
        resetRepository.save(token);

        // The full token handed to the user is "selector.verifier".
        String fullToken = selector + "." + verifier;
        String link = resetUrlBase + "?token=" + fullToken;

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", user.getName());
        vars.put("resetLink", link);
        vars.put("minutes", expirationMinutes);
        emailService.sendHtml(
                user.getEmail(),
                "AGIL Energy — Réinitialisation du mot de passe",
                "reset-password-email",
                vars
        );

        auditService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "User", user.getId(),
                "Lien de réinitialisation envoyé", clientIp(request));
        log.info("Password reset link issued for user {} (expires in {} min)", mask(email), expirationMinutes);
    }

    // ───────────────────────── Step 2: confirm ─────────────────────────

    @Override
    @Transactional
    public void confirmReset(String token, String newPassword, HttpServletRequest request) {
        String selector;
        String verifier;
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            throw new BusinessException("Lien invalide ou expiré");
        }
        selector = token.substring(0, dot);
        verifier = token.substring(dot + 1);

        PasswordResetToken prt = resetRepository.findBySelector(selector)
                .orElseThrow(() -> new BusinessException("Lien invalide ou expiré"));

        if (Boolean.TRUE.equals(prt.getUsed())) {
            throw new BusinessException("Ce lien a déjà été utilisé");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Lien expiré, veuillez refaire une demande");
        }
        if (prt.getAttempts() >= MAX_ATTEMPTS) {
            prt.setUsed(true);
            resetRepository.save(prt);
            throw new BusinessException("Trop de tentatives, veuillez refaire une demande");
        }

        if (!passwordEncoder.matches(verifier, prt.getTokenHash())) {
            prt.setAttempts(prt.getAttempts() + 1);
            resetRepository.save(prt);
            throw new BusinessException("Lien invalide ou expiré");
        }

        User user = prt.getUser();

        // Reject reusing the current password.
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("Le nouveau mot de passe doit être différent de l'ancien");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsed(true);
        resetRepository.save(prt);

        // Security: invalidate every existing session so a leaked old session can't persist.
        refreshTokenService.revokeAllForUser(user.getId());

        auditService.log(user.getId(), "PASSWORD_RESET_COMPLETED", "User", user.getId(),
                "Mot de passe réinitialisé; sessions révoquées", clientIp(request));
        log.info("Password reset completed for user {}", mask(user.getEmail()));

        // Courtesy confirmation email (best-effort; failures are swallowed by EmailService).
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", user.getName());
        emailService.sendHtml(
                user.getEmail(),
                "AGIL Energy — Votre mot de passe a été modifié",
                "password-changed-email",
                vars
        );
    }

    // ───────────────────────── Maintenance ─────────────────────────

    /** Daily cleanup of expired/used reset tokens (runs just after the OTP cleanup). */
    @Scheduled(cron = "0 35 3 * * *", zone = "Africa/Tunis")
    @Transactional
    public void cleanup() {
        int deleted = resetRepository.deleteExpiredOrUsed(LocalDateTime.now());
        if (deleted > 0) log.info("Password-reset cleanup: removed {} expired/used tokens", deleted);
    }

    // ───────────────────────── Helpers ─────────────────────────

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Mask an email for logs: a***@domain. */
    private static String mask(String email) {
        if (email == null || email.isBlank()) return "<none>";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }
}
