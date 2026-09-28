package com.simplebank.exception;

/** Thrown when a user or account ID doesn't exist. Becomes a 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " with id " + id + " was not found");
    }
}
