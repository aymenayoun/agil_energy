package com.agil.energy.security;

import com.agil.energy.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Choose a more specific message based on signal headers set by the JWT filter
        String message;
        if ("true".equals(response.getHeader("X-Token-Revoked"))) {
            message = "Token révoqué. Veuillez vous reconnecter.";
        } else if ("true".equals(response.getHeader("X-Token-Expired"))) {
            message = "Token expiré. Veuillez rafraîchir vos identifiants.";
        } else {
            message = "Authentification requise.";
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.error(message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}