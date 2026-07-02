package com.padelpro.auth.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Migration integration test for V11 (acceso-cuenta-prod, group 1, task 1.1):
 * adds {@code users.must_change_password BOOLEAN NOT NULL DEFAULT false}.
 *
 * <p>Runs the full Flyway chain V1..V11 on a real PostgreSQL 15 and verifies the new column
 * exists, is NOT NULL, and defaults to {@code false}. Mirrors {@link
 * com.padelpro.reservas.infrastructure.persistence.ReservationsMigrationIT} (Vía A vs
 * Testcontainers) so it runs both locally and in CI.
 */
@DisplayName("Migración V11 — users.must_change_password (Postgres real)")
class MustChangePasswordMigrationIT {

    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static PostgreSQLContainer<?> container;

    @BeforeAll
    static void migrate() {
        String externalUrl = System.getProperty("it.postgres.url");
        if (externalUrl != null && !externalUrl.isBlank()) {
            jdbcUrl = externalUrl;
            username = System.getProperty("it.postgres.user", "padelpro");
            password = System.getProperty("it.postgres.password", "padelpro_dev");
        } else {
            boolean dockerAvailable;
            try {
                dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable t) {
                dockerAvailable = false;
            }
            assumeTrue(dockerAvailable,
                    "Skipping migration ITs: no external it.postgres.url and Docker not reachable by Testcontainers");
            container = new PostgreSQLContainer<>("postgres:15-alpine");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        }

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .migrate();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(jdbcUrl, username, password);
    }

    // 1.1a — column exists, is boolean, NOT NULL, default false
    @Test
    @DisplayName("1.1a must_change_password column exists with NOT NULL default false")
    void must_change_password_column_exists_with_not_null_default_false() throws SQLException {
        try (Connection c = connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT data_type, is_nullable, column_default "
                             + "FROM information_schema.columns "
                             + "WHERE table_name = 'users' AND column_name = 'must_change_password'")) {
            assertThat(rs.next()).as("column must_change_password should exist").isTrue();
            assertThat(rs.getString("data_type")).isEqualTo("boolean");
            assertThat(rs.getString("is_nullable")).isEqualTo("NO");
            assertThat(rs.getString("column_default")).contains("false");
        }
    }

    // 1.1b — a fresh insert without specifying the column defaults to false
    @Test
    @DisplayName("1.1b new user row defaults must_change_password to false")
    void new_user_row_defaults_must_change_password_to_false() throws SQLException {
        try (Connection c = connection()) {
            long id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users (login, password_hash, first_name, last_name, email, status, role) "
                            + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'USER') RETURNING id")) {
                String unique = String.valueOf(System.nanoTime());
                ps.setString(1, "mcp" + unique.substring(unique.length() - 6));
                ps.setString(2, "$2a$12$abcdefghijklmnopqrstuv");
                ps.setString(3, "Must");
                ps.setString(4, "Change");
                ps.setString(5, "mcp" + unique + "@padelpro.local");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    id = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT must_change_password FROM users WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getBoolean("must_change_password")).isFalse();
                }
            } finally {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }
        }
    }
}
