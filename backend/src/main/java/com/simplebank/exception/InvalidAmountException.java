package com.simplebank.exception;

/** Thrown for zero, negative, or badly formatted amounts. Becomes a 400. */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
