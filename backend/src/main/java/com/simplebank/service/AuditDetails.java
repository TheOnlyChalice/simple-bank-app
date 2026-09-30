package com.simplebank.service;

import java.math.BigDecimal;

/**
 * What an audit event is about. Built up with the with...() methods, e.g.
 * AuditDetails.forAccount(7).withAmount(amount). Any field can be null.
 */
public record AuditDetails(
        Long userId,
        Long accountId,
        Long relatedAccountId,
        BigDecimal amount,
        String details
) {
    public static AuditDetails none() {
        return new AuditDetails(null, null, null, null, null);
    }

    public static AuditDetails forUser(Long userId) {
        return none().withUserId(userId);
    }

    public static AuditDetails forAccount(Long accountId) {
        return none().withAccountId(accountId);
    }

    public static AuditDetails forTransfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        return new AuditDetails(null, fromAccountId, toAccountId, amount, null);
    }

    public AuditDetails withUserId(Long value) {
        return new AuditDetails(value, accountId, relatedAccountId, amount, details);
    }

    public AuditDetails withAccountId(Long value) {
        return new AuditDetails(userId, value, relatedAccountId, amount, details);
    }

    public AuditDetails withAmount(BigDecimal value) {
        return new AuditDetails(userId, accountId, relatedAccountId, value, details);
    }

    public AuditDetails withDetails(String value) {
        return new AuditDetails(userId, accountId, relatedAccountId, amount, value);
    }
}
