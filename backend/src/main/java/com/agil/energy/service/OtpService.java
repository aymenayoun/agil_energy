package com.agil.energy.service;

import com.agil.energy.entity.User;

public interface OtpService {

    /** Returns whether 2FA is required for the given user, based on configuration. */
    boolean isOtpRequiredFor(User user);

    /** Generates an OTP for the user, persists a hashed copy, emails the code, and returns the opaque token id. */
    String generateAndSend(User user);

    /** Returns the user associated with the OTP if the code matches and is not expired/used. Throws otherwise. */
    User verify(String tokenId, String code);
}