package com.simplebank.exception;

/**
 * Thrown when a request makes no sense as asked, e.g. transferring to the
 * same account, or asking for a negative page number. Becomes a 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
