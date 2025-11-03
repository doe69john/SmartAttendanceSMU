package com.smartattendance.security;

/**
 * Enumerates the supported application roles.
 */
public enum Role {
    STUDENT,
    PROFESSOR,
    ADMIN;

    /**
     * Returns the Spring Security authority representation for this role.
     */
    public String asAuthority() {
        return "ROLE_" + name();
    }
}
