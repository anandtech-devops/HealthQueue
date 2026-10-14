package com.healthqueue.security;

import com.healthqueue.domain.Role;

/** The authenticated caller, as extracted from the JWT. */
public record AuthUser(Long id, String email, Role role) {
    public boolean isAdmin() { return role == Role.ADMIN; }
}
