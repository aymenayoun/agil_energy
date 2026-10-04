package com.agil.energy.controller;

import com.agil.energy.dto.request.ForgotPasswordRequest;
import com.agil.energy.dto.request.LoginRequest;
import com.agil.energy.dto.request.LogoutRequest;
import com.agil.energy.dto.request.RefreshTokenRequest;
import com.agil.energy.dto.request.ResetPasswordRequest;
import com.agil.energy.dto.request.VerifyOtpRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.AuthResponse;
import com.agil.energy.service.AuthService;
import com.agil.energy.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private PasswordResetService passwordResetService;
    @InjectMocks private AuthController controller;

    @Test
    @DisplayName("login — returns 200 with success message")
    void login_returnsSuccess() {
        LoginRequest req = new LoginRequest();
        AuthResponse mock = AuthResponse.builder().token("jwt").requiresOtp(false).build();
        when(authService.login(any(), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<AuthResponse>> response =
                controller.login(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).contains("Connexion");
        assertThat(response.getBody().getData().getToken()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("login — OTP required returns email-sent message")
    void login_otpRequired_returnsOtpMessage() {
        LoginRequest req = new LoginRequest();
        AuthResponse mock = AuthResponse.builder().requiresOtp(true).otpToken("tok").build();
        when(authService.login(any(), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<AuthResponse>> response =
                controller.login(req, new MockHttpServletRequest());

        assertThat(response.getBody().getMessage()).contains("vérification");
    }

    @Test
    @DisplayName("verifyOtp — returns 200 with auth response")
    void verifyOtp_returnsSuccess() {
        VerifyOtpRequest req = new VerifyOtpRequest();
        AuthResponse mock = AuthResponse.builder().token("jwt").build();
        when(authService.verifyOtp(any(), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<AuthResponse>> response =
                controller.verifyOtp(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getToken()).isEqualTo("jwt");
    }

    @Test
    @DisplayName("refresh — returns 200 with new tokens")
    void refresh_returnsSuccess() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        AuthResponse mock = AuthResponse.builder().token("new-jwt").build();
        when(authService.refresh(any(), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<AuthResponse>> response =
                controller.refresh(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getToken()).isEqualTo("new-jwt");
    }

    @Test
    @DisplayName("logout — extracts Bearer token and calls service")
    void logout_extractsBearerToken() {
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.addHeader("Authorization", "Bearer my-access-token");
        LogoutRequest req = new LogoutRequest();

        ResponseEntity<ApiResponse<Void>> response = controller.logout(req, httpReq);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).logout(req, "my-access-token");
    }

    @Test
    @DisplayName("logout — no Authorization header passes null token")
    void logout_noAuthHeader_passesNull() {
        LogoutRequest req = new LogoutRequest();

        ResponseEntity<ApiResponse<Void>> response =
                controller.logout(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).logout(req, null);
    }

    // ── forgot-password ───────────────────────────────────────

    @Test
    @DisplayName("forgotPassword — returns 200 with neutral anti-enumeration message")
    void forgotPassword_returnsNeutralMessage() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("user@agil.tn");

        ResponseEntity<ApiResponse<Void>> response =
                controller.forgotPassword(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        // Message must NOT confirm or deny the account's existence.
        assertThat(response.getBody().getMessage()).contains("Si un compte");
        verify(passwordResetService).requestReset(eq("user@agil.tn"), any());
    }

    @Test
    @DisplayName("forgotPassword — unknown email returns the SAME message (no enumeration)")
    void forgotPassword_unknownEmail_sameMessage() {
        ForgotPasswordRequest known = new ForgotPasswordRequest();
        known.setEmail("known@agil.tn");
        ForgotPasswordRequest unknown = new ForgotPasswordRequest();
        unknown.setEmail("ghost@agil.tn");

        String knownMsg = controller.forgotPassword(known, new MockHttpServletRequest())
                .getBody().getMessage();
        String unknownMsg = controller.forgotPassword(unknown, new MockHttpServletRequest())
                .getBody().getMessage();

        assertThat(knownMsg).isEqualTo(unknownMsg);
    }

    // ── reset-password ────────────────────────────────────────

    @Test
    @DisplayName("resetPassword — returns 200 and delegates to service")
    void resetPassword_returnsSuccess() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("sel.verifier");
        req.setNewPassword("NewPass123");

        ResponseEntity<ApiResponse<Void>> response =
                controller.resetPassword(req, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).contains("succès");
        verify(passwordResetService).confirmReset(eq("sel.verifier"), eq("NewPass123"), any());
    }
}
