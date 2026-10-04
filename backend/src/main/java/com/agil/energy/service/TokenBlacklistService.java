package com.agil.energy.service;

import java.time.LocalDateTime;

public interface TokenBlacklistService {

    /** Add an access token's jti to the blacklist until it would have expired anyway. */
    void blacklist(String jti, Long userId, LocalDateTime expiresAt, String reason);

    /** Returns true if the given jti has been revoked. */
    boolean isBlacklisted(String jti);
}