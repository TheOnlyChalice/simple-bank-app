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
    TRANSFER,
    /** A login attempt, successful or not. */
    LOGIN,
    /** A logged-in user tried to use something that isn't theirs. */
    ACCESS_DENIED
}
