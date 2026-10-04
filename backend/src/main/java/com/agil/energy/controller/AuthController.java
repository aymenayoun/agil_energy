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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        String message = response.isRequiresOtp()
                ? "Code de vérification envoyé par email"
                : "Connexion réussie";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.verifyOtp(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Connexion réussie", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Token rafraîchi", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordResetService.requestReset(request.getEmail(), httpRequest);
        // Always the same response, whether or not the email exists (anti-enumeration).
        return ResponseEntity.ok(ApiResponse.success(
                "Si un compte est associé à cet email, un lien de réinitialisation a été envoyé.",
                null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword(), httpRequest);
        return ResponseEntity.ok(ApiResponse.success(
                "Mot de passe réinitialisé avec succès. Vous pouvez maintenant vous connecter.",
                null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        String accessToken = extractAccessToken(httpRequest);
        authService.logout(request, accessToken);
        return ResponseEntity.ok(ApiResponse.success("Déconnexion réussie", null));
    }

    private String extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}