package com.simplebank.model;

public enum AuditOutcome {
    /** The action happened. */
    SUCCESS,
    /** A business rule refused it (e.g. insufficient funds). Nothing changed. */
    REJECTED,
    /** A system problem stopped it (e.g. the account stayed too busy). Nothing changed. */
    FAILED
}
