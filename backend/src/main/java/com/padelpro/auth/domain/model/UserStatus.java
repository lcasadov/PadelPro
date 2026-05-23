package com.padelpro.auth.domain.model;

/**
 * Lifecycle status of a platform user. Soft-delete is implemented as INACTIVE.
 * Stored as STRING in PostgreSQL (coherent with the CHECK constraint in V1__create_users_table.sql).
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    INACTIVE
}
