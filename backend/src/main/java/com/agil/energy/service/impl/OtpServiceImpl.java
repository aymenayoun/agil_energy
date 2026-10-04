package com.agil.energy.service.impl;

import com.agil.energy.entity.OtpToken;
import com.agil.energy.entity.User;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.OtpTokenRepository;
import com.agil.energy.service.EmailService;
import com.agil.energy.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository otpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.enabled}")
    private boolean otpEnabled;

    @Value("${app.otp.expiration-minutes}")
    private int expirationMinutes;

    @Value("${app.otp.required-roles}")
    private String requiredRoles;

    @Override
    public boolean isOtpRequiredFor(User user) {
        if (!otpEnabled) return false;
        if (user.getRole() == null) return false;
        String roleName = user.getRole().getName().toUpperCase(Locale.ROOT);
        String configured = requiredRoles == null ? "" : requiredRoles.trim().toUpperCase(Locale.ROOT);
        if (configured.isEmpty()) return false;
        if ("ALL".equals(configured)) return true;
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .anyMatch(roleName::equals);
    }

    @Override
    @Transactional
    public String generateAndSend(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String tokenId = UUID.randomUUID().toString();

        OtpToken otp = OtpToken.builder()
                .tokenId(tokenId)
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .used(false)
                .attempts(0)
                .build();
        otpRepository.save(otp);

        Map<String, Object> vars = new HashMap<>();
        vars.put("name", user.getName());
        vars.put("code", code);
        vars.put("minutes", expirationMinutes);
        emailService.sendHtml(
                user.getEmail(),
                "AGIL Energy — Code de vérification",
                "otp-email",
                vars
        );

        log.info("OTP generated for user {} (tokenId={}, expires in {} min)", user.getEmail(), tokenId, expirationMinutes);
        return tokenId;
    }

    @Override
    @Transactional
    public User verify(String tokenId, String code) {
        OtpToken otp = otpRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new BusinessException("Code invalide ou expiré"));

        if (Boolean.TRUE.equals(otp.getUsed())) {
            throw new BusinessException("Ce code a déjà été utilisé");
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Code expiré, veuillez vous reconnecter");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new BusinessException("Trop de tentatives, veuillez vous reconnecter");
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new BusinessException("Code invalide");
        }

        otp.setUsed(true);
        otpRepository.save(otp);
        return otp.getUser();
    }

    /** Daily cleanup of expired/used OTPs. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Tunis")
    @Transactional
    public void cleanup() {
        int deleted = otpRepository.deleteExpiredOrUsed(LocalDateTime.now());
        if (deleted > 0) log.info("OTP cleanup: removed {} expired/used tokens", deleted);
    }
}