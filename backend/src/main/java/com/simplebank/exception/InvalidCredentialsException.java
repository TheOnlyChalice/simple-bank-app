package com.simplebank.exception;

/**
 * Wrong email or password. Becomes a 401. The message is the same either way, so
 * the login form can't be used to find out which emails have accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
