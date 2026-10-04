package com.agil.energy.service;

import jakarta.servlet.http.HttpServletRequest;

public interface PasswordResetService {

    /**
     * Starts the reset flow for the given email. Always completes without revealing
     * whether the address exists (anti-enumeration). When a matching active user is
     * found, a single-use reset link is emailed.
     */
    void requestReset(String email, HttpServletRequest request);

    /**
     * Completes the reset: validates the emailed token, sets the new password,
     * consumes the token, and revokes all existing sessions for the user.
     * Throws {@link com.agil.energy.exception.BusinessException} on an invalid token.
     */
    void confirmReset(String token, String newPassword, HttpServletRequest request);
}
