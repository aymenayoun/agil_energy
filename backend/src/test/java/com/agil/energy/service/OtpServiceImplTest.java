package com.agil.energy.service;

import com.agil.energy.entity.OtpToken;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.repository.OtpTokenRepository;
import com.agil.energy.service.impl.OtpServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpTokenRepository otpRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private OtpServiceImpl otpService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpEnabled", true);
        ReflectionTestUtils.setField(otpService, "expirationMinutes", 5);
        ReflectionTestUtils.setField(otpService, "requiredRoles", "ADMIN,MANAGER");

        Role adminRole = new Role(); adminRole.setName("ADMIN");
        user = new User();
        user.setId(1L);
        user.setEmail("admin@agil.tn");
        user.setName("Admin User");
        user.setRole(adminRole);
    }

    // ── isOtpRequiredFor ──────────────────────────────────────

    @Test
    @DisplayName("isOtpRequiredFor — disabled globally returns false")
    void isOtpRequiredFor_disabled_returnsFalse() {
        ReflectionTestUtils.setField(otpService, "otpEnabled", false);
        assertThat(otpService.isOtpRequiredFor(user)).isFalse();
    }

    @Test
    @DisplayName("isOtpRequiredFor — user role matches configured roles returns true")
    void isOtpRequiredFor_matchingRole_returnsTrue() {
        assertThat(otpService.isOtpRequiredFor(user)).isTrue();
    }

    @Test
    @DisplayName("isOtpRequiredFor — user role does NOT match returns false")
    void isOtpRequiredFor_nonMatchingRole_returnsFalse() {
        Role normalRole = new Role(); normalRole.setName("USER");
        user.setRole(normalRole);
        assertThat(otpService.isOtpRequiredFor(user)).isFalse();
    }

    @Test
    @DisplayName("isOtpRequiredFor — 'ALL' configured returns true for any role")
    void isOtpRequiredFor_allConfigured_returnsTrue() {
        ReflectionTestUtils.setField(otpService, "requiredRoles", "ALL");
        Role anyRole = new Role(); anyRole.setName("WHATEVER");
        user.setRole(anyRole);
        assertThat(otpService.isOtpRequiredFor(user)).isTrue();
    }

    @Test
    @DisplayName("isOtpRequiredFor — null role returns false")
    void isOtpRequiredFor_nullRole_returnsFalse() {
        user.setRole(null);
        assertThat(otpService.isOtpRequiredFor(user)).isFalse();
    }

    @Test
    @DisplayName("isOtpRequiredFor — empty config returns false")
    void isOtpRequiredFor_emptyConfig_returnsFalse() {
        ReflectionTestUtils.setField(otpService, "requiredRoles", "");
        assertThat(otpService.isOtpRequiredFor(user)).isFalse();
    }

    // ── generateAndSend ───────────────────────────────────────

    @Test
    @DisplayName("generateAndSend — saves OTP, sends email, returns tokenId")
    void generateAndSend_savesAndSends() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        String tokenId = otpService.generateAndSend(user);

        assertThat(tokenId).isNotBlank();
        verify(otpRepository).save(any(OtpToken.class));
        verify(emailService).sendHtml(eq("admin@agil.tn"), anyString(), eq("otp-email"), anyMap());
    }

    // ── verify ────────────────────────────────────────────────

    @Test
    @DisplayName("verify — invalid tokenId throws BusinessException")
    void verify_unknownToken_throws() {
        when(otpRepository.findByTokenId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verify("nope", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("verify — already used token throws")
    void verify_alreadyUsed_throws() {
        OtpToken otp = OtpToken.builder()
                .tokenId("t1").user(user).codeHash("h").used(true)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(0).build();
        when(otpRepository.findByTokenId("t1")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify("t1", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà");
    }

    @Test
    @DisplayName("verify — expired token throws")
    void verify_expired_throws() {
        OtpToken otp = OtpToken.builder()
                .tokenId("t1").user(user).codeHash("h").used(false)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).attempts(0).build();
        when(otpRepository.findByTokenId("t1")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify("t1", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("verify — too many attempts throws")
    void verify_tooManyAttempts_throws() {
        OtpToken otp = OtpToken.builder()
                .tokenId("t1").user(user).codeHash("h").used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(5).build();
        when(otpRepository.findByTokenId("t1")).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> otpService.verify("t1", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tentatives");
    }

    @Test
    @DisplayName("verify — wrong code increments attempts and throws")
    void verify_wrongCode_incrementsAttempts() {
        OtpToken otp = OtpToken.builder()
                .tokenId("t1").user(user).codeHash("hashed").used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(0).build();
        when(otpRepository.findByTokenId("t1")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> otpService.verify("t1", "000000"))
                .isInstanceOf(BusinessException.class);

        assertThat(otp.getAttempts()).isEqualTo(1);
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("verify — valid code marks as used and returns user")
    void verify_validCode_returnsUser() {
        OtpToken otp = OtpToken.builder()
                .tokenId("t1").user(user).codeHash("hashed").used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).attempts(0).build();
        when(otpRepository.findByTokenId("t1")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        User result = otpService.verify("t1", "123456");

        assertThat(result).isEqualTo(user);
        assertThat(otp.getUsed()).isTrue();
        verify(otpRepository).save(otp);
    }

    @Test
    @DisplayName("cleanup — calls repository delete")
    void cleanup_deletesExpired() {
        when(otpRepository.deleteExpiredOrUsed(any())).thenReturn(7);
        otpService.cleanup();
        verify(otpRepository).deleteExpiredOrUsed(any());
    }
}