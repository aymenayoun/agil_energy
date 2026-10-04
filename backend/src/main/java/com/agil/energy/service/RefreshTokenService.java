package com.agil.energy.service;

import com.agil.energy.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface RefreshTokenService {

    /** Generates and stores a new refresh token. Returns the plaintext token to send to the client. */
    String generate(User user, HttpServletRequest request);

    /** Validates a presented refresh token and returns the owning user. Throws if invalid/expired/revoked. */
    User validateAndGetUser(String rawToken);

    /** Revokes a single refresh token. No-op if the token doesn't match. */
    void revoke(String rawToken);

    /** Revokes ALL refresh tokens for a given user (logout from all devices). */
    void revokeAllForUser(Long userId);

    /** Configured refresh-token lifetime in milliseconds. */
    long getRefreshExpirationMs();
}