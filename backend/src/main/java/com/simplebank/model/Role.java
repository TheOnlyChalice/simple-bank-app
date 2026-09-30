package com.simplebank.model;

public enum Role {
    /** A bank customer: can only see and use their own profile and accounts. */
    CUSTOMER,
    /** Bank staff: can see and do everything, including the audit log. */
    ADMIN
}
