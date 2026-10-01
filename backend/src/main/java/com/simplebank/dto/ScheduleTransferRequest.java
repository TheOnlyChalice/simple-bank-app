package com.simplebank.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Body for POST /api/transfers/scheduled. scheduledFor is a UTC timestamp,
 * e.g. "2026-10-15T14:30:00Z". The amount and time rules are checked in ScheduledTransferService.
 */
public record ScheduleTransferRequest(
        @NotNull(message = "fromAccountId is required")
        Long fromAccountId,

        @NotNull(message = "toAccountId is required")
        Long toAccountId,

        @NotNull(message = "amount is required")
        BigDecimal amount,

        @NotNull(message = "scheduledFor is required")
        Instant scheduledFor
) {
}
