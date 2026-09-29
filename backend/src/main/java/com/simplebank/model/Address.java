package com.simplebank.model;

/**
 * A postal address, stored inside the user document (an "embedded document").
 * It has no ID of its own because it always belongs to exactly one user.
 */
public record Address(String street, String city, String state, String zip) {
}
