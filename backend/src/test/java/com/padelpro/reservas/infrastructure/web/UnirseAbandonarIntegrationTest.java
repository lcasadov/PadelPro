package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for join/leave a match against a real PostgreSQL (capability partidas, D2/D3):
 * {@code POST /api/reservas/{id}/unirse} and {@code DELETE /api/reservas/{id}/participacion}.
 */
@DisplayName("IT — unirse / abandonar partida (Postgres real)")
class UnirseAbandonarIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User owner;
    private User joiner;
    private User joiner2;
    private User joiner3;
    private User outsider;
    private String ownerToken;
    private String joinerToken;
    private String joiner2Token;
    private String joiner3Token;
    private String outsiderToken;

    private final LocalDate futureDate = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        owner = save("owner.user", "Owner", "User");
        joiner = save("joiner.user", "Jane", "Smith");
        joiner2 = save("joiner2.user", "Juan", "Dos");
        joiner3 = save("joiner3.user", "Ken", "Tres");
        outsider = save("outsider.user", "Out", "Sider");
        ownerToken = jwtService.generateAccessToken(owner);
        joinerToken = jwtService.generateAccessToken(joiner);
        joiner2Token = jwtService.generateAccessToken(joiner2);
        joiner3Token = jwtService.generateAccessToken(joiner3);
        outsiderToken = jwtService.generateAccessToken(outsider);
    }

    private User save(String login, String first, String last) {
        OffsetDateTime now = OffsetDateTime.now();
        return userRepository.saveAndFlush(new User(login, passwordEncoder.encode("Password1"),
                first, last, login + "@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
    }

    private String createReserva(String startTime,
                                 List<CrearReservaRequest.ParticipanteAdicional> extras) throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, 60, "test", extras));
        MvcResult result = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ── unirse ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2.1 unión exitosa → 200 con datos del participante y +1 en participants")
    void should_join_successfully() throws Exception {
        String id = createReserva("18:00", null);

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId", is(id)))
                .andExpect(jsonPath("$.userId", is(joiner.getId().intValue())))
                .andExpect(jsonPath("$.nombre", is("Jane Smith")))
                .andExpect(jsonPath("$.statusPago", is("PENDING")))
                .andExpect(jsonPath("$.participanteId").isNumber());

        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        assertThat(r.getParticipants()).hasSize(2);
        assertThat(r.getParticipants()).anySatisfy(p -> {
            assertThat(p.getUserId()).isEqualTo(joiner.getId());
            assertThat(p.getSlotPosition()).isEqualTo(2);
            assertThat(p.isOwner()).isFalse();
        });
    }

    @Test
    @DisplayName("2.1 usuario ya participante (el owner) → 409 CONFLICT")
    void should_return_409_when_owner_tries_to_join_own_reservation() throws Exception {
        String id = createReserva("18:00", null);

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }

    @Test
    @DisplayName("2.1 unirse dos veces → segunda vez 409 CONFLICT (sin duplicar)")
    void should_return_409_when_joining_twice() throws Exception {
        String id = createReserva("18:00", null);
        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));

        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        assertThat(r.getParticipants()).hasSize(2);
    }

    @Test
    @DisplayName("2.1 reserva completa → 422 PARTICIPANTS_LIMIT_EXCEEDED")
    void should_return_422_when_full() throws Exception {
        // owner + 3 registered → 4/4 full
        String id = createReserva("18:00", List.of(
                new CrearReservaRequest.ParticipanteAdicional(joiner.getId(), null, null),
                new CrearReservaRequest.ParticipanteAdicional(joiner2.getId(), null, null),
                new CrearReservaRequest.ParticipanteAdicional(joiner3.getId(), null, null)));

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("PARTICIPANTS_LIMIT_EXCEEDED")));
    }

    @Test
    @DisplayName("2.1 reserva inexistente → 404")
    void should_return_404_when_reservation_absent() throws Exception {
        mockMvc.perform(post("/api/reservas/{id}/unirse", UUID.randomUUID())
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2.1 reserva CANCELLED → 422 RESERVA_NOT_JOINABLE")
    void should_return_422_when_cancelled() throws Exception {
        String id = createReserva("18:00", null);
        // Owner cancels within deadline.
        mockMvc.perform(delete("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("RESERVA_NOT_JOINABLE")));
    }

    @Test
    @DisplayName("2.1 reserva COMPLETED → 422 RESERVA_NOT_JOINABLE")
    void should_return_422_when_completed() throws Exception {
        String id = createReserva("18:00", null);
        // Move the reservation to a terminal, non-active state directly (no ADMIN endpoint here).
        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        r.changeStatus(ReservationStatus.COMPLETED);
        reservationRepository.saveAndFlush(r);

        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("RESERVA_NOT_JOINABLE")));
    }

    @Test
    @DisplayName("2.1 sin JWT → 401")
    void should_return_401_when_anonymous_join() throws Exception {
        String id = createReserva("18:00", null);
        mockMvc.perform(post("/api/reservas/{id}/unirse", id))
                .andExpect(status().isUnauthorized());
    }

    // ── abandonar ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3.1 participante no-owner abandona → 204 y libera la plaza")
    void should_leave_and_free_seat() throws Exception {
        String id = createReserva("18:00", null);
        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/reservas/{id}/participacion", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isNoContent());

        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        assertThat(r.getParticipants()).hasSize(1);
        assertThat(r.getParticipants()).noneSatisfy(
                p -> assertThat(p.getUserId()).isEqualTo(joiner.getId()));
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING_CONFIRMATION);
    }

    @Test
    @DisplayName("3.1 el owner no puede abandonar → 422 OWNER_CANNOT_ABANDON")
    void should_reject_owner_leaving() throws Exception {
        String id = createReserva("18:00", null);

        mockMvc.perform(delete("/api/reservas/{id}/participacion", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("OWNER_CANNOT_ABANDON")));

        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        assertThat(r.getParticipants()).hasSize(1);
    }

    @Test
    @DisplayName("3.1 no participante intenta abandonar → 422 NOT_A_PARTICIPANT")
    void should_reject_non_participant_leaving() throws Exception {
        String id = createReserva("18:00", null);

        mockMvc.perform(delete("/api/reservas/{id}/participacion", id)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("NOT_A_PARTICIPANT")));
    }

    @Test
    @DisplayName("3.1 abandonar reserva inexistente → 404")
    void should_return_404_when_leaving_absent_reservation() throws Exception {
        mockMvc.perform(delete("/api/reservas/{id}/participacion", UUID.randomUUID())
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("3.1 abandonar reserva CANCELLED → 422 RESERVA_NOT_JOINABLE")
    void should_reject_leaving_cancelled_reservation() throws Exception {
        String id = createReserva("18:00", null);
        // The joiner becomes a participant, then the owner cancels the reservation.
        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/reservas/{id}", id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Abandoning a non-active reservation is rejected before the participant check.
        mockMvc.perform(delete("/api/reservas/{id}/participacion", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("RESERVA_NOT_JOINABLE")));
    }

    @Test
    @DisplayName("3.1 abandonar reserva COMPLETED → 422 RESERVA_NOT_JOINABLE")
    void should_reject_leaving_completed_reservation() throws Exception {
        String id = createReserva("18:00", null);
        mockMvc.perform(post("/api/reservas/{id}/unirse", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk());
        Reservation r = reservationRepository.findByIdWithParticipants(UUID.fromString(id)).orElseThrow();
        r.changeStatus(ReservationStatus.COMPLETED);
        reservationRepository.saveAndFlush(r);

        mockMvc.perform(delete("/api/reservas/{id}/participacion", id)
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("RESERVA_NOT_JOINABLE")));
    }
}
