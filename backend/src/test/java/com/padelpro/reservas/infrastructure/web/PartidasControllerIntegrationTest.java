package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code GET /api/partidas} (capability partidas, D1) against a real PostgreSQL.
 *
 * <p>Verifies the open-matches contract: reservations with free seats appear with {@code reservaId},
 * hora, plazas, importe informativo and participant display names only (no PII); full reservations are
 * hidden; anonymous requests get 401.
 */
@DisplayName("IT — GET /api/partidas (Postgres real)")
class PartidasControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User owner;
    private User p2;
    private User p3;
    private User p4;
    private String ownerToken;

    private final LocalDate futureDate = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        owner = userRepository.saveAndFlush(new User("owner.user", passwordEncoder.encode("Password1"),
                "Owner", "User", "owner@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        p2 = userRepository.saveAndFlush(new User("p2.user", passwordEncoder.encode("Password1"),
                "Pepe", "Dos", "p2@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        p3 = userRepository.saveAndFlush(new User("p3.user", passwordEncoder.encode("Password1"),
                "Tres", "Tres", "p3@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        p4 = userRepository.saveAndFlush(new User("p4.user", passwordEncoder.encode("Password1"),
                "Cuatro", "Cuatro", "p4@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        ownerToken = jwtService.generateAccessToken(owner);
    }

    private void createReserva(String startTime, List<CrearReservaRequest.ParticipanteAdicional> extras)
            throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), startTime, 60, "test", extras));
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("1.1 lista partidas abiertas con reservaId, hora, plazasLibres, importe y participantes (sin PII)")
    void should_list_open_matches_with_identifier_and_no_pii() throws Exception {
        createReserva("18:00", null); // owner only → 3 free seats

        mockMvc.perform(get("/api/partidas").param("fecha", futureDate.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reservaId").exists())
                .andExpect(jsonPath("$[0].startTime", is("18:00:00")))
                .andExpect(jsonPath("$[0].durationMinutes", is(60)))
                .andExpect(jsonPath("$[0].plazasLibres", is(3)))
                .andExpect(jsonPath("$[0].priceTotal", is(15.00)))
                .andExpect(jsonPath("$[0].participantes", hasSize(1)))
                .andExpect(jsonPath("$[0].participantes[0].nombre", is("Owner User")))
                .andExpect(jsonPath("$[0].participantes[0].owner", is(true)))
                // RGPD: no email/phone leaked in the projection
                .andExpect(jsonPath("$[0].participantes[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].participantes[0].externalPhone").doesNotExist());
    }

    @Test
    @DisplayName("1.1 reserva completa (4/4) no aparece en el listado")
    void should_hide_full_reservation() throws Exception {
        // Full reservation at 18:00 (owner + 3 registered) and an open one at 20:00 (owner only).
        createReserva("18:00", List.of(
                new CrearReservaRequest.ParticipanteAdicional(p2.getId(), null, null),
                new CrearReservaRequest.ParticipanteAdicional(p3.getId(), null, null),
                new CrearReservaRequest.ParticipanteAdicional(p4.getId(), null, null)));
        createReserva("20:00", null);

        mockMvc.perform(get("/api/partidas").param("fecha", futureDate.toString())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startTime", is("20:00:00")))
                .andExpect(jsonPath("$[0].plazasLibres", is(3)));
    }

    @Test
    @DisplayName("1.1 sin JWT → 401")
    void should_return_401_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/partidas").param("fecha", futureDate.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("1.1 fecha ausente → 400 VALIDATION_ERROR")
    void should_return_400_when_missing_fecha() throws Exception {
        mockMvc.perform(get("/api/partidas")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }
}
