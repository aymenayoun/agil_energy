package com.agil.energy.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutRequest {

    /** Optional. If provided, this specific refresh token is revoked. */
    private String refreshToken;

    /** If true, revokes ALL the user's refresh tokens (logout from all devices). */
    @Builder.Default
    private boolean allDevices = false;
}