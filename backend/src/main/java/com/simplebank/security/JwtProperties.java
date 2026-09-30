package com.simplebank.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The app.jwt.* settings. The app refuses to start without a proper signing key,
 * so it can never run with a weak or missing one.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {

    /** HS256 needs a key of at least 256 bits (32 bytes). */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.startsWith("${")
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " characters. Set the JWT_SECRET environment variable.");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "simple-bank";
        }
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
