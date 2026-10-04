package com.agil.energy.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String type;
    private Long userId;
    private String name;
    private String email;
    private String role;

    /** Station scope for STATION_MANAGER users; null otherwise. */
    private Long stationId;
    private String stationName;

    /** Region scope for MANAGER users; null otherwise. */
    private String region;

    private String refreshToken;
    private Long   expiresIn;
    private Long   refreshExpiresIn;

    @Builder.Default
    private boolean requiresOtp = false;
    private String otpToken;
    private Integer otpExpiresInSeconds;
}