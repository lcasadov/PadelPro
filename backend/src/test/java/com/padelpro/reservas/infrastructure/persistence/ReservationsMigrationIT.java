package com.padelpro.reservas.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migration integration tests for V7 (reservations + participants), tasks 2.1–2.4.
 *
 * <p>Runs the full Flyway chain V1..V7 on a real PostgreSQL 15 (Testcontainers) and verifies the
 * anti-overlap exclusion constraint behaves as designed (D-RES-01). H2 cannot host {@code EXCLUDE
 * USING gist}/{@code btree_gist}, so these checks require a real engine.
 *
 * <p>The class self-disables when Docker is unavailable ({@code disabledWithoutDocker = true}),
 * so the rest of the suite (H2-based) still runs locally without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migración V7 — reservations + participants (Testcontainers)")
class ReservationsMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection connection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Inserts the seed user/owner reused by overlap tests. Returns the generated user id. */
    private long insertOwner(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (login, password_hash, first_name, last_name, phone, email, status, role) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'USER') RETURNING id",
                Statement.RETURN_GENERATED_KEYS)) {
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
