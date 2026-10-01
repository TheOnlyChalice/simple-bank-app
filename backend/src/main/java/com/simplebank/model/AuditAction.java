package com.simplebank.model;

/** What was done (or attempted). */
public enum AuditAction {
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    ACCOUNT_CREATED,
    ACCOUNT_UPDATED,
    ACCOUNT_DELETED,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    /** A transfer was scheduled for later (it is audited again as TRANSFER when it runs). */
    TRANSFER_SCHEDULED,
    /** A pending scheduled transfer was cancelled. */
    SCHEDULED_TRANSFER_CANCELLED,
    /** A login attempt, successful or not. */
    LOGIN,
    /** A logged-in user tried to use something that isn't theirs. */
    ACCESS_DENIED
}
