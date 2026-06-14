package com.padelpro.reservas.infrastructure.persistence;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Migration integration tests for V7 (reservations + participants), tasks 2.1–2.4.
 *
 * <p>Runs the full Flyway chain V1..V7 on a real PostgreSQL 15 and verifies the anti-overlap
 * exclusion constraint behaves as designed (D-RES-01). H2 cannot host {@code EXCLUDE USING gist}/
 * {@code btree_gist}, so these checks require a real engine.
 *
 * <p><b>Two ways to provide that PostgreSQL</b> (Vía A vs Testcontainers):
 * <ul>
 *   <li><b>External (default for local runs):</b> pass system properties
 *       {@code -Dit.postgres.url=jdbc:postgresql://localhost:5433/padelpro_it
 *       -Dit.postgres.user=padelpro -Dit.postgres.password=padelpro_dev}. The test connects over
 *       plain JDBC/TCP to a PostgreSQL started via {@code docker compose}/{@code docker run},
 *       so it does NOT touch docker-java/Testcontainers — useful where the Docker Desktop named
 *       pipe breaks the Testcontainers client.</li>
 *   <li><b>Testcontainers (default for CI):</b> if {@code it.postgres.url} is absent, spin an
 *       ephemeral {@code postgres:15-alpine} container. Requires a reachable Docker daemon; the
 *       whole class is skipped (assumption) when neither an external URL nor Docker is available.</li>
 * </ul>
 */
@DisplayName("Migración V7 — reservations + participants (Postgres real)")
class ReservationsMigrationIT {

    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static PostgreSQLContainer<?> container;

    @BeforeAll
    static void migrate() {
        String externalUrl = System.getProperty("it.postgres.url");
        if (externalUrl != null && !externalUrl.isBlank()) {
            // Vía A — external PostgreSQL (docker compose / docker run), no Testcontainers.
            jdbcUrl = externalUrl;
            username = System.getProperty("it.postgres.user", "padelpro");
            password = System.getProperty("it.postgres.password", "padelpro_dev");
        } else {
            // CI fallback — ephemeral container via Testcontainers (needs a working Docker daemon).
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

    /** Inserts the seed user/owner reused by overlap tests. Returns the generated user id. */
    private long insertOwner(Connection c) throws SQLException {
        // Use RETURNING id with a plain executeQuery(); do NOT combine with RETURN_GENERATED_KEYS,
        // since the PostgreSQL driver then yields no ResultSet ("query returned no results").
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (login, password_hash, first_name, last_name, phone, email, status, role) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'USER') RETURNING id")) {
            String unique = String.valueOf(System.nanoTime());
            ps.setString(1, "owner" + unique.substring(unique.length() - 6));
            ps.setString(2, "$2a$12$abcdefghijklmnopqrstuv");
            ps.setString(3, "Owner");
            ps.setString(4, "Test");
            ps.setString(5, "+3460" + unique.substring(unique.length() - 7));
            ps.setString(6, "owner" + unique + "@padelpro.local");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertReservation(Connection c, long ownerId, String date,
                                    String start, String end, int minutes, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reservations (owner_id, reservation_date, start_time, end_time, " +
                        "duration_minutes, status, channel) " +
                        "VALUES (?, ?::date, ?::time, ?::time, ?, ?::reservation_status, 'WEB'::reservation_channel)")) {
            ps.setLong(1, ownerId);
            ps.setString(2, date);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setInt(5, minutes);
            ps.setString(6, status);
            ps.executeUpdate();
        }
    }

    // 2.1 — V1..V7 aplican limpiamente (si migrate() falla, @BeforeAll aborta la clase)
    @Test
    @DisplayName("2.1 migrations_v1_to_v7_apply_cleanly")
    void migrations_v1_to_v7_apply_cleanly() throws SQLException {
        try (Connection c = connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT to_regclass('public.reservations') AS r, to_regclass('public.participants') AS p")) {
            rs.next();
            assertThat(rs.getString("r")).isEqualTo("reservations");
            assertThat(rs.getString("p")).isEqualTo("participants");
        }
    }

    // 2.2 — btree_gist disponible tras V7
    @Test
    @DisplayName("2.2 btree_gist_extension_is_available")
    void btree_gist_extension_is_available() throws SQLException {
        try (Connection c = connection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT count(*) AS n FROM pg_extension WHERE extname = 'btree_gist'")) {
            rs.next();
            assertThat(rs.getInt("n")).isEqualTo(1);
        }
    }

    // 2.3 — dos reservas activas solapadas → la BD rechaza la segunda
    @Test
    @DisplayName("2.3 overlapping_active_reservations_are_rejected")
    void overlapping_active_reservations_are_rejected() throws SQLException {
        try (Connection c = connection()) {
            long owner = insertOwner(c);
            insertReservation(c, owner, "2030-01-10", "10:00", "11:00", 60, "CONFIRMED");

            assertThatThrownBy(() ->
                    insertReservation(c, owner, "2030-01-10", "10:30", "11:30", 60, "PENDING_CONFIRMATION"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("excl_res_no_overlap");
        }
    }

    // 2.4 — la segunda CANCELLED SÍ se permite (constraint parcial WHERE status <> 'CANCELLED')
    @Test
    @DisplayName("2.4 overlapping_with_second_cancelled_is_allowed")
    void overlapping_with_second_cancelled_is_allowed() throws SQLException {
        try (Connection c = connection()) {
            long owner = insertOwner(c);
            insertReservation(c, owner, "2030-02-10", "12:00", "13:00", 60, "CONFIRMED");
            // Second one CANCELLED at the same window — exempt from the partial exclusion constraint.
            insertReservation(c, owner, "2030-02-10", "12:00", "13:00", 60, "CANCELLED");

            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT count(*) AS n FROM reservations WHERE reservation_date = '2030-02-10'")) {
                rs.next();
                assertThat(rs.getInt("n")).isEqualTo(2);
            }
        }
    }
}
