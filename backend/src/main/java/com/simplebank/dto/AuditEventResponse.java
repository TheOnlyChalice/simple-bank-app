package com.simplebank.dto;

import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditEvent;
import com.simplebank.model.AuditOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One audit event. The timestamp is in UTC, e.g. "2026-09-30T14:02:11.482Z". */
public record AuditEventResponse(
        Long auditId,
        String referenceId,
        Instant timestamp,
        String actor,
        AuditAction action,
        AuditOutcome outcome,
        String reason,
        Long userId,
        Long accountId,
        Long relatedAccountId,
        BigDecimal amount,
        List<Long> transactionIds,
        String details
) {
    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getAuditId(),
                event.getReferenceId(),
                event.getTimestamp(),
                event.getActor(),
                event.getAction(),
                event.getOutcome(),
                event.getReason(),
                event.getUserId(),
                event.getAccountId(),
                event.getRelatedAccountId(),
                event.getAmount(),
                event.getTransactionIds() == null ? List.of() : event.getTransactionIds(),
                event.getDetails());
    }
}
