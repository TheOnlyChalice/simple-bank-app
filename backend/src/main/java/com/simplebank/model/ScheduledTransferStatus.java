package com.simplebank.model;

public enum ScheduledTransferStatus {
    /** Waiting for its date and time. */
    PENDING,
    /** The money moved. */
    COMPLETED,
    /** It was due, but a rule refused it (e.g. insufficient funds, a frozen account). Never retried. */
    FAILED,
    /** Cancelled by the customer or the bank before it ran. */
    CANCELLED
}
