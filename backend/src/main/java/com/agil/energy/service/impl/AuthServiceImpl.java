package com.agil.energy.service.impl;

import com.agil.energy.dto.request.LoginRequest;
import com.agil.energy.dto.request.LogoutRequest;
import com.agil.energy.dto.request.RefreshTokenRequest;
import com.agil.energy.dto.request.VerifyOtpRequest;
import com.agil.energy.dto.response.AuthResponse;
import com.agil.energy.entity.User;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.security.JwtUtil;
import com.agil.energy.service.AuthService;
import com.agil.energy.service.OtpService;
import com.agil.energy.service.RefreshTokenService;
import com.agil.energy.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Override
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));

        if (otpService.isOtpRequiredFor(user)) {
            String tokenId = otpService.generateAndSend(user);
            return AuthResponse.builder()
                    .requiresOtp(true)
                    .otpToken(tokenId)
                    .otpExpiresInSeconds(otpExpirationMinutes * 60)
                    .build();
        }

        return buildAuthenticatedResponse(userDetails, user, httpRequest);
    }

    @Override
    public AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        User user = otpService.verify(request.getOtpToken(), request.getCode());
        CustomUserDetails details = new CustomUserDetails(user);
        return buildAuthenticatedResponse(details, user, httpRequest);
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        User user = refreshTokenService.validateAndGetUser(request.getRefreshToken());

        refreshTokenService.revoke(request.getRefreshToken());
        String newRefreshToken = refreshTokenService.generate(user, httpRequest);

        CustomUserDetails details = new CustomUserDetails(user);
        String accessToken = jwtUtil.generateToken(
                details, details.getId(), details.getRole(),
                details.getStationId(), details.getRegion());

        return AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .userId(details.getId())
                .name(details.getName())
                .email(details.getEmail())
                .role(details.getRole())
                .stationId(details.getStationId())
                .stationName(details.getStationName())
                .region(details.getRegion())
                .refreshToken(newRefreshToken)
                .expiresIn(jwtUtil.getAccessTokenExpirationMs() / 1000)
                .refreshExpiresIn(refreshTokenService.getRefreshExpirationMs() / 1000)
                .requiresOtp(false)
                .build();
    }

    @Override
    public void logout(LogoutRequest request, String accessToken) {
        Long userId = null;

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                String jti = jwtUtil.extractJti(accessToken);
                userId = jwtUtil.extractUserId(accessToken);
                Date exp = jwtUtil.extractExpiration(accessToken);
                LocalDateTime expiresAt = exp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                tokenBlacklistService.blacklist(jti, userId, expiresAt, "LOGOUT");
            } catch (Exception e) {
                log.warn("Logout blacklist skipped: {}", e.getMessage());
            }
        }

        if (request != null && request.isAllDevices() && userId != null) {
            refreshTokenService.revokeAllForUser(userId);
        } else if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            refreshTokenService.revoke(request.getRefreshToken());
        }
    }

    private AuthResponse buildAuthenticatedResponse(CustomUserDetails userDetails, User user,
                                                    HttpServletRequest httpRequest) {
        String accessToken = jwtUtil.generateToken(
                userDetails, userDetails.getId(), userDetails.getRole(),
                userDetails.getStationId(), userDetails.getRegion());
        String refreshToken = refreshTokenService.generate(user, httpRequest);

        return AuthResponse.builder()
                .token(accessToken)
                .type("Bearer")
                .userId(userDetails.getId())
                .name(userDetails.getName())
                .email(userDetails.getEmail())
                .role(userDetails.getRole())
                .stationId(userDetails.getStationId())
                .stationName(userDetails.getStationName())
                .region(userDetails.getRegion())
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getAccessTokenExpirationMs() / 1000)
                .refreshExpiresIn(refreshTokenService.getRefreshExpirationMs() / 1000)
                .requiresOtp(false)
                .build();
    }
}