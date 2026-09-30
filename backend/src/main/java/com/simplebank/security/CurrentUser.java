package com.simplebank.security;

import com.simplebank.model.Role;

/** Who is logged in, as read from their token. */
public record CurrentUser(Long userId, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
