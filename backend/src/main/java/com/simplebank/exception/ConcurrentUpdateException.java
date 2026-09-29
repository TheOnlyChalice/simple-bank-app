package com.simplebank.exception;

/**
 * Thrown when a transaction kept hitting write conflicts (other requests changing the
 * same account at the same moment) for longer than the retry budget. Becomes a 503:
 * nothing was changed, and the client can safely try again.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(Throwable cause) {
        super("The account is busy with other requests. Please try again.", cause);
    }
}
