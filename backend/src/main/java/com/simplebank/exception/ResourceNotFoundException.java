package com.simplebank.exception;

/** Thrown when something with the given ID doesn't exist. Becomes a 404. */
public class ResourceNotFoundException extends RuntimeException {

    /** The ID can be a number (users, accounts) or text (audit reference IDs). */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " with id " + id + " was not found");
    }
}
