package com.agil.energy.service;

import com.agil.energy.dto.request.LoginRequest;
import com.agil.energy.dto.request.LogoutRequest;
import com.agil.energy.dto.request.RefreshTokenRequest;
import com.agil.energy.dto.request.VerifyOtpRequest;
import com.agil.energy.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest);

    AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest);

    void logout(LogoutRequest request, String accessToken);
}