package com.simplebank.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/auth/login. */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
}
