package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CambiarEstadoRequest;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ADMIN reservation management against a real PostgreSQL 15
 * (capability reservas, US-007 / #14, task 8.7 — coverage gap on {@code AdminReservaService} and the
 * {@code PATCH /api/admin/reservas/{id}/estado} endpoint, which previously had no repeatable IT).
 *
 * <p>Covers the unidirectional state machine driven by an ADMIN (D6), the cancellation-policy bypass
 * (D-RES-03 — ADMIN cancels regardless of deadline), the PAID→REFUNDED side effect on admin cancel,
 * invalid transitions → 422, and the role gate (USER → 403). Also exercises the additional-participants
 * creation path ({@code CrearReservaRequest.ParticipanteAdicional}, {@code Participant.registered/external})
 * and the real availability read path through {@code ReservationQueryAdapter}.
 *
 * <p>Extends {@link PostgresIntegrationTest}; JWTs are minted via {@link JwtService} so the
 * {@code JwtAuthFilter} authenticates the principal and {@code @PreAuthorize}/security chain enforce roles.
 */
@DisplayName("IT — /api/admin/reservas estado (Postgres real)")
class AdminReservaIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User owner;
    private User other;
    private User admin;
    private String ownerToken;
    private String otherToken;
    private String adminToken;

    private final LocalDate futureDate = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        owner = userRepository.saveAndFlush(new User(
                "owner.admit", passwordEncoder.encode("Password1"), "Owner", "User",
                "owner.admit@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        other = userRepository.saveAndFlush(new User(
                "other.admit", passwordEncoder.encode("Password1"), "Other", "User",
                "other.admit@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        admin = userRepository.saveAndFlush(new User(
                "admin.admit", passwordEncoder.encode("Password1"), "Admin", "User",
                "admin.admit@example.com", UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        ownerToken = jwtService.generateAccessToken(owner);
        otherToken = jwtService.generateAccessToken(other);
        adminToken = jwtService.generateAccessToken(admin);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String reservaJson(String startTime, int durationMinutes) throws Exception {
        return objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, durationMinutes, "test", null));
    }

    private String createReserva(String token, String startTime, int duration) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(startTime, duration)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(
            String token, String id, String status) throws Exception {
        return mockMvc.perform(patch("/api/admin/reservas/{id}/estado", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CambiarEstadoRequest(status))));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.7 — PATCH estado: valid transitions driven by ADMIN (D6)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.7 ADMIN confirma reserva PENDING_CONFIRMATION → CONFIRMED → 200, pago sigue PENDING (D5 cash)")
    void admin_confirms_pending_reservation() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                // MVP cash path: confirming does NOT mark the payment paid (collected separately).
                .andExpect(jsonPath("$.pago.status", is("PENDING")));

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("8.7 ADMIN CONFIRMED → COMPLETED → 200 (estado terminal alcanzado)")
    void admin_completes_confirmed_reservation() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);
        changeStatus(adminToken, id, "CONFIRMED").andExpect(status().isOk());

        changeStatus(adminToken, id, "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    @DisplayName("8.7 ADMIN cancela reserva con bypass de política (D-RES-03) → 200 CANCELLED")
    void admin_cancels_with_policy_bypass() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("8.7 ADMIN cancela reserva con pago PAID → pago pasa a REFUNDED")
    void admin_cancel_refunds_paid_payment() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        // Simulate that the reservation was already paid (e.g. cash registered / gateway callback).
        Payment payment = paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow();
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.saveAndFlush(payment);

        changeStatus(adminToken, id, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.pago.status", is("REFUNDED")));

        assertThat(paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("8.7 ADMIN cancela reserva con pago PENDING → pago NO se reembolsa (sigue PENDING)")
    void admin_cancel_does_not_refund_pending_payment() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pago.status", is("PENDING")));

        assertThat(paymentRepository.findByReservationId(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.7 — invalid transitions / inputs → 422 / 400 / 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.7 transición inválida PENDING_CONFIRMATION → COMPLETED → 422 INVALID_STATE_TRANSITION")
    void invalid_transition_pending_to_completed_422() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "COMPLETED")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_CONFIRMATION);
    }

    @Test
    @DisplayName("8.7 transición no-op (mismo estado) → 422 INVALID_STATE_TRANSITION")
    void noop_transition_same_state_422() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "PENDING_CONFIRMATION")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));
    }

    @Test
    @DisplayName("8.7 transición desde estado terminal CANCELLED → 422")
    void transition_from_terminal_cancelled_422() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);
        changeStatus(adminToken, id, "CANCELLED").andExpect(status().isOk());

        changeStatus(adminToken, id, "CONFIRMED")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_STATE_TRANSITION")));
    }

    @Test
    @DisplayName("8.7 status desconocido en el body → 400 VALIDATION_ERROR")
    void unknown_status_value_400() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "FOO_BAR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("8.7 status en blanco en el body → 400 VALIDATION_ERROR")
    void blank_status_value_400() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(adminToken, id, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("8.7 PATCH estado de reserva inexistente → 404 RESERVA_NOT_FOUND")
    void patch_nonexistent_reservation_404() throws Exception {
        changeStatus(adminToken, UUID.randomUUID().toString(), "CONFIRMED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESERVA_NOT_FOUND")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8.7 — role gate on PATCH estado
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8.7 PATCH estado por USER → 403 (endpoint admin)")
    void patch_estado_by_user_403() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        changeStatus(otherToken, id, "CONFIRMED")
                .andExpect(status().isForbidden());

        // Estado intacto.
        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING_CONFIRMATION);
    }

    @Test
    @DisplayName("8.7 PATCH estado sin JWT → 401")
    void patch_estado_without_jwt_401() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);

        mockMvc.perform(patch("/api/admin/reservas/{id}/estado", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CambiarEstadoRequest("CONFIRMED"))))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // additional participants — Participant.registered / external + ParticipanteAdicional DTO
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crea reserva con participante registrado adicional → 201 con 2 participantes")
    void create_with_registered_additional_participant() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "dobles",
                List.of(new CrearReservaRequest.ParticipanteAdicional(other.getId(), null, null))));

        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        Reservation saved = reservationRepository.findByIdWithParticipants(id).orElseThrow();
        assertThat(saved.getParticipants()).hasSize(2);
        assertThat(saved.getParticipants()).anySatisfy(p -> {
            assertThat(p.getUserId()).isEqualTo(other.getId());
            assertThat(p.isOwner()).isFalse();
            assertThat(p.getSlotPosition()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("crea reserva con invitado externo adicional → 201 con externalName persistido")
    void create_with_external_additional_participant() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "con invitado",
                List.of(new CrearReservaRequest.ParticipanteAdicional(null, "Invitado Externo", "+34600111222"))));

        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        Reservation saved = reservationRepository.findByIdWithParticipants(id).orElseThrow();
        assertThat(saved.getParticipants()).anySatisfy(p -> {
            assertThat(p.getExternalName()).isEqualTo("Invitado Externo");
            assertThat(p.getExternalPhone()).isEqualTo("+34600111222");
            assertThat(p.getUserId()).isNull();
        });
    }

    @Test
    @DisplayName("participante adicional con userId Y externalName → 400 VALIDATION_ERROR (XOR)")
    void additional_participant_with_both_user_and_external_400() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "invalido",
                List.of(new CrearReservaRequest.ParticipanteAdicional(other.getId(), "Externo", null))));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("participante adicional sin userId ni externalName → 400 VALIDATION_ERROR (XOR)")
    void additional_participant_with_neither_400() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "invalido",
                List.of(new CrearReservaRequest.ParticipanteAdicional(null, null, null))));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // availability read path — exercises ReservationQueryAdapter.findActiveOccupanciesByDate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /disponibles refleja una reserva activa (camino real ReservationQueryAdapter)")
    void availability_reflects_active_reservation() throws Exception {
        // No reservations yet: the 18:00 60-min slot is fully free (3 plazas).
        mockMvc.perform(get("/api/reservas/disponibles")
                        .param("fecha", futureDate.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        createReserva(ownerToken, "18:00", 60);

        // After an active reservation, the adapter maps occupancy and free slots drop for 18:00.
        MvcResult result = mockMvc.perform(get("/api/reservas/disponibles")
                        .param("fecha", futureDate.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("\"fecha\"");
        assertThat(json).contains(futureDate.toString());
    }

    @Test
    @DisplayName("GET /disponibles tras CANCELLED por admin libera la franja (occupancy excluye CANCELLED)")
    void availability_excludes_cancelled_reservation() throws Exception {
        String id = createReserva(ownerToken, "18:00", 60);
        changeStatus(adminToken, id, "CANCELLED").andExpect(status().isOk());

        // A cancelled reservation must not count toward occupancy; query path must succeed.
        mockMvc.perform(get("/api/reservas/disponibles")
                        .param("fecha", futureDate.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
