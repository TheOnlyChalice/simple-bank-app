package com.simplebank.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The app.admin.* settings: the first admin account, created at startup if missing. */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String email, String password) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
