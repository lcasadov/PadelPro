package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.SystemConfigRepository;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the reservas write/read path against a real PostgreSQL 15.
 * Covers tasks 8.2 (atomic creation), 8.4 (idempotency), 8.5 (authorization/BOLA), 8.6 (frozen price).
 *
 * <p>Extends {@link PostgresIntegrationTest}, which wires the datasource to either an external
 * PostgreSQL (Vía A, {@code -Dit.postgres.url=...}) or an ephemeral Testcontainers container (CI),
 * with {@code @Sql} cleanup before each test. JWTs are minted via
 * {@link JwtService#generateAccessToken(User)} so the {@code JwtAuthFilter} sets the user id as the
 * principal (the controller reads it as the reservation owner).
 *
 * <p>H2 cannot host the {@code excl_res_no_overlap} gist constraint, so these run only on Postgres.
 */
@DisplayName("IT — /api/reservas (Postgres real)")
class ReservaControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SystemConfigRepository systemConfigRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User owner;
    private User otherUser;
    private User admin;
    private String ownerToken;
    private String otherToken;
    private String adminToken;

    /** A fixed future date so the "reservation must be in the future" validation always passes. */
    private final LocalDate futureDate = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        owner = userRepository.saveAndFlush(new User(
                "owner.user", passwordEncoder.encode("Password1"), "Owner", "User",
                "owner@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        otherUser = userRepository.saveAndFlush(new User(
                "other.user", passwordEncoder.encode("Password1"), "Other", "User",
                "other@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        admin = userRepository.saveAndFlush(new User(
                "admin.user", passwordEncoder.encode("Password1"), "Admin", "User",
                "admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        ownerToken = jwtService.generateAccessToken(owner);
        otherToken = jwtService.generateAccessToken(otherUser);
        adminToken = jwtService.generateAccessToken(admin);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String reservaJson(String startTime, int durationMinutes) throws Exception {
        return objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, durationMinutes, "test", null));
    }

    /** Build a create-reservation body with a single registered-participant {@code userId}. */
    private String reservaJsonWithRegisteredParticipant(String startTime, int durationMinutes, Long userId)
            throws Exception {
        return objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, durationMinutes, "test",
                java.util.List.of(new CrearReservaRequest.ParticipanteAdicional(userId, null, null))));
    }

    private MvcResult createReserva(String token, String startTime, int duration, String idempotencyKey)
            throws Exception {
        var req = post("/api/reservas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservaJson(startTime, duration));
        if (idempotencyKey != null) {
            req = req.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(req).andReturn();
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.2 — atomic creation (reservation + participant(owner) + payment PENDING)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.2 POST crea reserva + participante owner + pago PENDING atómicamente → 201")
    void should_create_reservation_participant_and_pending_payment_atomically() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson("18:00", 90)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId", is(owner.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING_CONFIRMATION")))
                .andExpect(jsonPath("$.durationMinutes", is(90)))
                // price frozen = 15.00 €/h × 90 / 60 = 22.50
                .andExpect(jsonPath("$.priceTotal", is(22.50)))
                .andExpect(jsonPath("$.pago.status", is("PENDING")))
                .andExpect(jsonPath("$.participants", org.hamcrest.Matchers.hasSize(1)))
                .andReturn();

        UUID reservationId = UUID.fromString(extractId(result));

        // The 3 records must all be present and consistent in the DB (one transaction).
        assertThat(reservationRepository.findByIdWithParticipants(reservationId)).isPresent();
        assertThat(reservationRepository.findByIdWithParticipants(reservationId).get().getParticipants())
                .hasSize(1)
                .allSatisfy(p -> assertThat(p.getUserId()).isEqualTo(owner.getId()));

        Payment payment = paymentRepository.findByReservationId(reservationId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("22.50");
    }

    @Test
    @DisplayName("8.2 segundo POST en franja solapada (secuencial) → 409 CONFLICT (constraint gist)")
    void should_return_409_when_overlapping_slot_sequential() throws Exception {
        // First reservation 18:00–19:00 succeeds.
        createReserva(ownerToken, "18:00", 60, null);

        // Overlapping 18:30–19:30 by a different user must be rejected by the gist constraint → 409.
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson("18:30", 60)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));

        // No orphan payment for a reservation that never committed.
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("8.2 reserva no solapada en la misma fecha → 201 (ambas coexisten)")
    void should_create_two_non_overlapping_reservations_same_date() throws Exception {
        createReserva(ownerToken, "18:00", 60, null);
        createReserva(otherToken, "19:00", 60, null);

        assertThat(reservationRepository.findAll()).hasSize(2);
        assertThat(paymentRepository.findAll()).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.4 — idempotency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.4 misma Idempotency-Key (mismo user) → mismo recurso, sin duplicar reserva ni pago")
    void should_return_same_resource_when_same_idempotency_key() throws Exception {
        String key = "idem-key-123";
        MvcResult first = createReserva(ownerToken, "18:00", 60, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        String firstId = extractId(first);

        MvcResult second = createReserva(ownerToken, "18:00", 60, key);
        String secondId = extractId(second);

        assertThat(secondId).isEqualTo(firstId);
        // Exactly one reservation and one payment despite two POSTs.
        assertThat(reservationRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("8.4 misma key, body distinto → devuelve la reserva original (D2, no detecta divergencia)")
    void should_return_original_reservation_when_same_key_different_body() throws Exception {
        String key = "idem-key-body";
        MvcResult first = createReserva(ownerToken, "18:00", 60, key);
        String firstId = extractId(first);

        // Same key but a different slot/duration — D2 returns the original resource unchanged.
        MvcResult second = createReserva(ownerToken, "20:00", 120, key);
        String secondId = extractId(second);

        assertThat(secondId).isEqualTo(firstId);
        assertThat(reservationRepository.findAll()).hasSize(1);
        // The original 60-min reservation is preserved, the new body is ignored.
        assertThat(reservationRepository.findById(UUID.fromString(firstId)).orElseThrow()
                .getDurationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("8.4 key nueva → nueva reserva")
    void should_create_new_reservation_when_new_key() throws Exception {
        createReserva(ownerToken, "18:00", 60, "key-A");
        createReserva(ownerToken, "19:00", 60, "key-B");

        assertThat(reservationRepository.findAll()).hasSize(2);
        assertThat(paymentRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("8.4 misma key por usuarios distintos → reservas distintas (scope por user)")
    void should_scope_idempotency_key_per_user() throws Exception {
        String key = "shared-key";
        createReserva(ownerToken, "18:00", 60, key);
        // Same key, different user, non-overlapping slot → independent reservation.
        createReserva(otherToken, "19:00", 60, key);

        assertThat(reservationRepository.findAll()).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.5 — authorization / BOLA
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.5 GET /{id} por no-owner/no-participante → 403 FORBIDDEN (no 404, BOLA)")
    void should_return_403_not_404_on_detail_for_non_owner() throws Exception {
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("8.5 GET /{id} para reserva inexistente → 403 (no revela existencia)")
    void should_return_403_for_nonexistent_reservation() throws Exception {
        mockMvc.perform(get("/api/reservas/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8.5 GET /{id} por el owner → 200")
    void should_return_200_on_detail_for_owner() throws Exception {
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)));
    }

    @Test
    @DisplayName("8.5 GET /{id} por ADMIN (no participante) → 200")
    void should_return_200_on_detail_for_admin() throws Exception {
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("8.5 GET / lista solo las reservas del usuario (owner/participante, RN-AUTH-01)")
    void should_list_only_own_reservations() throws Exception {
        createReserva(ownerToken, "18:00", 60, null);
        createReserva(otherToken, "19:00", 60, null);

        mockMvc.perform(get("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].ownerId", is(owner.getId().intValue())));
    }

    @Test
    @DisplayName("8.5 DELETE /{id} por no-owner → 403 FORBIDDEN")
    void should_return_403_when_non_owner_cancels() throws Exception {
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));

        mockMvc.perform(delete("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));

        // Reservation not cancelled.
        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow()
                .getStatus().name()).isEqualTo("PENDING_CONFIRMATION");
    }

    @Test
    @DisplayName("8.5 DELETE /{id} por el owner dentro de plazo → 204")
    void should_return_204_when_owner_cancels_within_deadline() throws Exception {
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));

        mockMvc.perform(delete("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow()
                .getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("8.5 GET /api/admin/reservas por USER → 403")
    void should_return_403_admin_list_for_user_role() throws Exception {
        mockMvc.perform(get("/api/admin/reservas")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8.5 GET /api/admin/reservas por ADMIN → 200 con todas las reservas")
    void should_return_200_admin_list_for_admin_role() throws Exception {
        createReserva(ownerToken, "18:00", 60, null);
        createReserva(otherToken, "19:00", 60, null);

        mockMvc.perform(get("/api/admin/reservas")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("8.5 POST sin JWT → 401")
    void should_return_401_when_no_jwt_on_create() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson("18:00", 60)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8.5 GET / sin JWT → 401")
    void should_return_401_when_no_jwt_on_list() throws Exception {
        mockMvc.perform(get("/api/reservas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8.5 GET /api/admin/reservas sin JWT → 401")
    void should_return_401_when_no_jwt_on_admin_list() throws Exception {
        mockMvc.perform(get("/api/admin/reservas"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.6 — US-024: changing price_per_hour does NOT alter existing reservations' amount
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.6 cambiar price_per_hour NO altera el amount de reservas ya creadas (precio congelado)")
    void should_freeze_amount_when_price_per_hour_changes_later() throws Exception {
        // Create with the seeded 15.00 €/h → 60 min = 15.00.
        String id = extractId(createReserva(ownerToken, "18:00", 60, null));
        Payment before = paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow();
        assertThat(before.getAmount()).isEqualByComparingTo("15.00");

        // Admin raises the hourly price to 30.00.
        SystemConfig config = ((com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort)
                systemConfigRepository).findById(1L).orElseThrow();
        config.setPricePerHour(new BigDecimal("30.00"));
        systemConfigRepository.saveAndFlush(config);

        // The existing payment amount is unchanged (frozen snapshot, D3 / US-024).
        Payment after = paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow();
        assertThat(after.getAmount()).isEqualByComparingTo("15.00");

        // A NEW reservation picks up the new price (30.00 €/h × 60 / 60 = 30.00).
        String newId = extractId(createReserva(ownerToken, "19:00", 60, null));
        Payment newPayment = paymentRepository.findByReservationId(UUID.fromString(newId)).orElseThrow();
        assertThat(newPayment.getAmount()).isEqualByComparingTo("30.00");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // input validation (400) — completeness of the create contract
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("validación: duración no permitida → 400 VALIDATION_ERROR")
    void should_return_400_when_invalid_duration() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson("18:00", 45)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("validación: fecha en el pasado → 400 VALIDATION_ERROR")
    void should_return_400_when_date_in_past() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                LocalDate.now().minusDays(1).toString(), "18:00", 60, "test", null));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("validación: startTime fuera de :00/:30 → 400 VALIDATION_ERROR")
    void should_return_400_when_start_time_not_on_half_hour() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson("18:15", 60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registered-participant validation (security): a userId attached to a
    // reservation must reference a real, ACTIVE member.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("seguridad: participante con userId inexistente → 422 PARTICIPANT_NOT_FOUND (no persiste)")
    void should_return_422_when_registered_participant_does_not_exist() throws Exception {
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJsonWithRegisteredParticipant("18:00", 60, 999999L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("PARTICIPANT_NOT_FOUND")));

        // Rolled back: nothing was persisted.
        assertThat(reservationRepository.findAll()).isEmpty();
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("seguridad: participante con userId de usuario no-ACTIVE → 422 (no persiste)")
    void should_return_422_when_registered_participant_not_active() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        User pending = userRepository.saveAndFlush(new User(
                "pending.user", passwordEncoder.encode("Password1"), "Pending", "User",
                "pending@example.com", UserRole.USER, UserStatus.PENDING, now, now));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJsonWithRegisteredParticipant("18:00", 60, pending.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("PARTICIPANT_NOT_FOUND")));

        assertThat(reservationRepository.findAll()).isEmpty();
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("seguridad: participante con userId ACTIVE válido → 201 con 2 participantes")
    void should_create_reservation_when_registered_participant_is_active() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJsonWithRegisteredParticipant("18:00", 60, otherUser.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        UUID reservationId = UUID.fromString(extractId(result));
        assertThat(reservationRepository.findByIdWithParticipants(reservationId).orElseThrow()
                .getParticipants()).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RN-RGPD-03 — PII minimization in the reservation detail for co-participants.
    // A co-player who joined an open match must NOT see guest phones nor the notes;
    // the owner and ADMIN still see everything. Access rule (403) is unchanged.
    // ─────────────────────────────────────────────────────────────────────────

    /** Owner-created reservation with private notes and an external guest carrying a phone. */
    private String createReservaWithGuestAndNotes(String startTime) throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, 60, "notas privadas del owner",
                java.util.List.of(new CrearReservaRequest.ParticipanteAdicional(
                        null, "Invitado Externo", "+34611223344"))));
        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result);
    }

    /** Make {@code otherUser} join the reservation as a non-owner co-participant. */
    private void joinAs(String id, String token) throws Exception {
        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RGPD: owner ve externalPhone del invitado y notes de la reserva")
    void should_expose_pii_to_owner() throws Exception {
        String id = createReservaWithGuestAndNotes("18:00");

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", is("notas privadas del owner")))
                .andExpect(jsonPath("$.participants[1].externalName", is("Invitado Externo")))
                .andExpect(jsonPath("$.participants[1].externalPhone", is("+34611223344")));
    }

    @Test
    @DisplayName("RGPD: ADMIN (no participante) ve externalPhone y notes")
    void should_expose_pii_to_admin() throws Exception {
        String id = createReservaWithGuestAndNotes("18:00");

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", is("notas privadas del owner")))
                .andExpect(jsonPath("$.participants[1].externalPhone", is("+34611223344")));
    }

    @Test
    @DisplayName("RGPD: co-participante (no owner) recibe externalPhone y notes en null")
    void should_minimize_pii_for_co_participant() throws Exception {
        String id = createReservaWithGuestAndNotes("18:00");
        joinAs(id, otherToken); // otherUser becomes a non-owner co-participant (slot 3)

        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                // The guest is still listed by display name, but the phone is nulled out.
                .andExpect(jsonPath("$.participants[1].externalName", is("Invitado Externo")))
                .andExpect(jsonPath("$.participants[1].externalPhone").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    @DisplayName("RGPD: no-participante sigue recibiendo 403 (regla de acceso intacta)")
    void should_still_return_403_for_non_participant() throws Exception {
        String id = createReservaWithGuestAndNotes("18:00");
        // otherUser has NOT joined → still no access.
        mockMvc.perform(get("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("RGPD: en el listado, el co-participante tampoco ve phone/notes de reservas ajenas")
    void should_minimize_pii_in_list_for_co_participant() throws Exception {
        String id = createReservaWithGuestAndNotes("18:00");
        joinAs(id, otherToken);

        // otherUser lists their reservations: this one (not owned) must come minimized.
        mockMvc.perform(get("/api/reservas")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].notes").doesNotExist())
                .andExpect(jsonPath("$[0].participants[1].externalPhone").doesNotExist());
    }
}
