package com.simplebank.exception;

/** Thrown when registering an email that's already taken. Becomes a 409. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("A user with email " + email + " already exists");
    }
}
