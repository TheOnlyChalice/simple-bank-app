package com.simplebank.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Body for deposit and withdraw. Only checks that an amount was sent;
 * the business rules (positive, max 2 decimals, enough balance) live in
 * AccountService so they hold no matter who calls the service.
 */
public record AmountRequest(
        @NotNull(message = "amount is required")
        BigDecimal amount
) {
}
