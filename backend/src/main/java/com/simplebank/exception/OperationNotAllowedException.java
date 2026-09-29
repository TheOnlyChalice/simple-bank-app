package com.simplebank.exception;

/**
 * Thrown when a request is valid but conflicts with the current state,
 * e.g. deleting an account that still has money in it. Becomes a 409.
 */
public class OperationNotAllowedException extends RuntimeException {

    public OperationNotAllowedException(String message) {
        super(message);
    }
}
