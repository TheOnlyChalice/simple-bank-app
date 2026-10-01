package com.simplebank.dto;

import com.simplebank.model.ScheduledTransfer;
import com.simplebank.model.ScheduledTransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** A scheduled transfer. Times are UTC (they end in "Z"); failureReason is only set for FAILED. */
public record ScheduledTransferResponse(
        Long scheduledTransferId,
        Long ownerUserId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        Instant scheduledFor,
        ScheduledTransferStatus status,
        Instant createdAt,
        Instant processedAt,
        String failureReason
) {
    public static ScheduledTransferResponse from(ScheduledTransfer transfer) {
        return new ScheduledTransferResponse(
                transfer.getScheduledTransferId(),
                transfer.getOwnerUserId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount(),
                transfer.getScheduledFor(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getProcessedAt(),
                transfer.getFailureReason());
    }
}
