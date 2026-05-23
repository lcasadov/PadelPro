package com.padelpro.auth.domain.model;

/**
 * Role assigned to a platform user. Stored as STRING in PostgreSQL
 * (coherent with the CHECK constraint in V1__create_users_table.sql).
 */
public enum UserRole {
    ADMIN,
    USER
}
