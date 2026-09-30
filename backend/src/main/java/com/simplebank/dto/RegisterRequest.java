package com.simplebank.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for POST /api/auth/register. Everyone who registers becomes a CUSTOMER. */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be 100 characters or fewer")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be 100 characters or fewer")
        String email,

        /** 8-72 characters with at least one letter and one number (BCrypt uses at most 72 bytes). */
        @NotBlank(message = "password is required")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "password must be 8-72 characters and include at least one letter and one number")
        String password,

        @NotNull(message = "address is required")
        @Valid
        AddressDto address
) {
}
