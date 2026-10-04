package com.agil.energy.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "YWdpbC1lbmVyZ3ktc2VjcmV0LWtleS1mb3Itand0LXRva2VuLTIwMjUtcGZl");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);

        user = new User("admin@agil.tn", "pwd",
                Collections.singletonList(() -> "ROLE_ADMIN"));
    }

    @Test
    @DisplayName("generateToken — produces a parsable JWT")
    void generateToken_producesValidJwt() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername — returns subject")
    void extractUsername_returnsSubject() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin@agil.tn");
    }

    @Test
    @DisplayName("extractUserId — returns userId claim")
    void extractUserId_returnsClaim() {
        String token = jwtUtil.generateToken(user, 42L, "ADMIN", null, null);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("extractRole — returns role claim")
    void extractRole_returnsClaim() {
        String token = jwtUtil.generateToken(user, 1L, "MANAGER", null, "Tunis");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("extractJti — returns generated JTI")
    void extractJti_returnsId() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        String jti = jwtUtil.extractJti(token);
        assertThat(jti).isNotBlank();
        assertThat(jti).hasSize(36);
    }

    @Test
    @DisplayName("extractExpiration — returns future date")
    void extractExpiration_returnsFutureDate() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        assertThat(jwtUtil.extractExpiration(token)).isAfter(new java.util.Date());
    }

    @Test
    @DisplayName("validateToken — valid token returns true")
    void validateToken_valid_returnsTrue() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        assertThat(jwtUtil.validateToken(token, user)).isTrue();
    }

    @Test
    @DisplayName("validateToken — wrong user returns false")
    void validateToken_wrongUser_returnsFalse() {
        String token = jwtUtil.generateToken(user, 1L, "ADMIN", null, null);
        UserDetails other = new User("other@agil.tn", "pwd",
                Collections.singletonList(() -> "ROLE_USER"));
        assertThat(jwtUtil.validateToken(token, other)).isFalse();
    }

    @Test
    @DisplayName("getAccessTokenExpirationMs — returns configured value")
    void getAccessTokenExpirationMs_returnsConfigured() {
        assertThat(jwtUtil.getAccessTokenExpirationMs()).isEqualTo(3_600_000L);
    }
}