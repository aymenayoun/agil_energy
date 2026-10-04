package com.agil.energy.service;

import com.agil.energy.dto.request.LoginRequest;
import com.agil.energy.dto.request.LogoutRequest;
import com.agil.energy.dto.request.RefreshTokenRequest;
import com.agil.energy.dto.request.VerifyOtpRequest;
import com.agil.energy.dto.response.AuthResponse;
import com.agil.energy.entity.User;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.security.JwtUtil;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private OtpService otpService;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private Authentication authentication;
    @Mock private CustomUserDetails userDetails;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "otpExpirationMinutes", 5);
    }

    // ─── login ────────────────────────────────────────────────

    @Test
    @DisplayName("login — returns AuthResponse with token (OTP not required)")
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@agil.tn");
        request.setPassword("password");

        User user = new User(); user.setId(1L);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn(1L);
        when(userDetails.getName()).thenReturn("Admin");
        when(userDetails.getEmail()).thenReturn("admin@agil.tn");
        when(userDetails.getRole()).thenReturn("ADMIN");
        when(userDetails.getStationId()).thenReturn(null);
        when(userDetails.getRegion()).thenReturn(null);
        when(userDetails.getStationName()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(otpService.isOtpRequiredFor(user)).thenReturn(false);
        when(jwtUtil.generateToken(any(), eq(1L), eq("ADMIN"), isNull(), isNull()))
                .thenReturn("mock-jwt-token");
        when(jwtUtil.getAccessTokenExpirationMs()).thenReturn(86400000L);
        when(refreshTokenService.generate(any(User.class), any(HttpServletRequest.class))).thenReturn("mock-refresh-token");
        when(refreshTokenService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.login(request, httpServletRequest);

        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.isRequiresOtp()).isFalse();
    }

    @Test
    @DisplayName("login — returns OTP response when required")
    void login_otpRequired_returnsOtpResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@agil.tn"); request.setPassword("password");

        User user = new User(); user.setId(1L);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(otpService.isOtpRequiredFor(user)).thenReturn(true);
        when(otpService.generateAndSend(user)).thenReturn("otp-token-123");

        AuthResponse response = authService.login(request, httpServletRequest);

        assertThat(response.isRequiresOtp()).isTrue();
        assertThat(response.getOtpToken()).isEqualTo("otp-token-123");
        verify(jwtUtil, never()).generateToken(any(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("login — throws BadCredentialsException on bad password")
    void login_invalidCredentials_throws() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@agil.tn"); request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request, httpServletRequest))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ─── verifyOtp ────────────────────────────────────────────

    @Test
    @DisplayName("verifyOtp — successful verification returns full auth response")
    void verifyOtp_success_returnsAuthResponse() {
        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setOtpToken("otp-tok"); req.setCode("123456");

        User user = new User();
        user.setId(1L); user.setEmail("admin@agil.tn"); user.setName("Admin");
        user.setStatus(com.agil.energy.enums.UserStatus.ACTIVE);
        com.agil.energy.entity.Role role = new com.agil.energy.entity.Role();
        role.setName("ADMIN");
        user.setRole(role);

        when(otpService.verify("otp-tok", "123456")).thenReturn(user);
        when(jwtUtil.generateToken(any(), eq(1L), eq("ADMIN"), isNull(), isNull()))
                .thenReturn("jwt");
        when(jwtUtil.getAccessTokenExpirationMs()).thenReturn(86400000L);
        when(refreshTokenService.generate(eq(user), any())).thenReturn("refresh");
        when(refreshTokenService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.verifyOtp(req, httpServletRequest);

        assertThat(response.getToken()).isEqualTo("jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.isRequiresOtp()).isFalse();
    }

    // ─── refresh ──────────────────────────────────────────────

    @Test
    @DisplayName("refresh — rotates tokens (revokes old + issues new)")
    void refresh_rotatesTokens() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("old-refresh");

        User user = new User();
        user.setId(1L); user.setEmail("u@x.com"); user.setName("U");
        user.setStatus(com.agil.energy.enums.UserStatus.ACTIVE);
        com.agil.energy.entity.Role role = new com.agil.energy.entity.Role();
        role.setName("ADMIN");
        user.setRole(role);

        when(refreshTokenService.validateAndGetUser("old-refresh")).thenReturn(user);
        when(refreshTokenService.generate(eq(user), any())).thenReturn("new-refresh");
        when(jwtUtil.generateToken(any(), eq(1L), eq("ADMIN"), isNull(), isNull()))
                .thenReturn("new-jwt");
        when(jwtUtil.getAccessTokenExpirationMs()).thenReturn(86400000L);
        when(refreshTokenService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.refresh(req, httpServletRequest);

        assertThat(response.getToken()).isEqualTo("new-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).revoke("old-refresh");
    }

    // ─── logout ───────────────────────────────────────────────

    @Test
    @DisplayName("logout — blacklists access token and revokes refresh token")
    void logout_blacklistsAndRevokes() {
        LogoutRequest req = new LogoutRequest();
        req.setRefreshToken("refresh-1");
        req.setAllDevices(false);

        when(jwtUtil.extractJti("access-token")).thenReturn("jti-1");
        when(jwtUtil.extractUserId("access-token")).thenReturn(1L);
        when(jwtUtil.extractExpiration("access-token"))
                .thenReturn(new Date(System.currentTimeMillis() + 60_000));

        authService.logout(req, "access-token");

        verify(tokenBlacklistService).blacklist(eq("jti-1"), eq(1L), any(), eq("LOGOUT"));
        verify(refreshTokenService).revoke("refresh-1");
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    @DisplayName("logout — allDevices true revokes all refresh tokens for user")
    void logout_allDevices_revokesAllForUser() {
        LogoutRequest req = new LogoutRequest();
        req.setAllDevices(true);

        when(jwtUtil.extractJti("access-token")).thenReturn("jti-1");
        when(jwtUtil.extractUserId("access-token")).thenReturn(1L);
        when(jwtUtil.extractExpiration("access-token"))
                .thenReturn(new Date(System.currentTimeMillis() + 60_000));

        authService.logout(req, "access-token");

        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    @DisplayName("logout — malformed token is silently swallowed")
    void logout_malformedToken_swallowsException() {
        LogoutRequest req = new LogoutRequest();
        req.setRefreshToken("ref-1");
        req.setAllDevices(false);

        when(jwtUtil.extractJti("garbage")).thenThrow(new RuntimeException("malformed"));

        authService.logout(req, "garbage");

        verify(tokenBlacklistService, never()).blacklist(any(), any(), any(), any());
        verify(refreshTokenService).revoke("ref-1");
    }

    @Test
    @DisplayName("logout — null access token still revokes refresh token")
    void logout_nullAccessToken_stillRevokesRefresh() {
        LogoutRequest req = new LogoutRequest();
        req.setRefreshToken("ref-1");

        authService.logout(req, null);

        verifyNoInteractions(tokenBlacklistService);
        verify(refreshTokenService).revoke("ref-1");
    }
}