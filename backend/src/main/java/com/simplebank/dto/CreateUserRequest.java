package com.simplebank.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be 100 characters or fewer")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must be 100 characters or fewer")
        String email,

        /** @Valid also checks the fields inside the address. */
        @NotNull(message = "address is required")
        @Valid
        AddressDto address
) {
}
