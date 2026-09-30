package com.simplebank.model;

/** What was done (or attempted). */
public enum AuditAction {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ACCOUNT_DELETED,
    DEPOSIT,
    WITHDRAW,
    TRANSFER
}
