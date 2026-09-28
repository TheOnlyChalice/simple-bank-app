package com.simplebank.dto;

import com.simplebank.model.AccountType;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "accountType is required (SAVINGS or CHECKING)")
        AccountType accountType
) {
}
