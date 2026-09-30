package com.simplebank.repository;

import com.simplebank.model.AuditAction;
import com.simplebank.model.AuditOutcome;

import java.time.Instant;

/**
 * Optional filters for searching the audit log. Any field left null is ignored.
 * accountId matches either side of a transfer.
 */
public record AuditFilter(
        Long accountId,
        Long userId,
        AuditAction action,
        AuditOutcome outcome,
        Instant from,
        Instant to
) {
    public static final AuditFilter NONE = new AuditFilter(null, null, null, null, null, null);
}
