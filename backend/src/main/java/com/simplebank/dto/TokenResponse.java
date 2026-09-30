package com.simplebank.dto;

import java.time.Instant;

/**
 * Returned by register and login. Send accessToken on every other request as
 * "Authorization: Bearer <accessToken>" until expiresAt (UTC), then log in again.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user
) {
}
