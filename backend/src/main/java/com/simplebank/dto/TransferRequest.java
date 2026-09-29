package com.simplebank.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Body for POST /api/transfers. The amount rules are checked in AccountService. */
public record TransferRequest(
        @NotNull(message = "fromAccountId is required")
        Long fromAccountId,

        @NotNull(message = "toAccountId is required")
        Long toAccountId,

        @NotNull(message = "amount is required")
        BigDecimal amount
) {
}
