package com.consoleadda.entity;

public enum RoleCode {
    ADMIN,
    OPERATOR;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
