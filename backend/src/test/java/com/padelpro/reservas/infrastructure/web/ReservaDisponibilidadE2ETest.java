package com.padelpro.reservas.infrastructure.web;

import com.padelpro.reservas.application.dto.DisponibilidadResponse;
import com.padelpro.reservas.application.dto.TramoDisponible;
import com.padelpro.reservas.application.service.DisponibilidadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2E tests for {@link ReservaDisponibilidadController} with Spring Security + MockMvc
 * (capability disponibilidad-pistas, US-006 / #13). Covers tasks 4.1–4.7.
 *
 * <p>The {@link DisponibilidadService} bean is replaced by a Mockito mock so these tests focus on
 * the HTTP contract (OpenAPI {@code getDisponibilidad}), authentication (401), authorization
 * (USER/ADMIN allowed) and {@code fecha} validation (400 {@code VALIDATION_ERROR}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("E2E — GET /api/reservas/disponibles")
class ReservaDisponibilidadE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DisponibilidadService disponibilidadService;

    // 4.1 — USER autenticado → 200 con tramos
    @Test
    @DisplayName("4.1 user_authenticated_gets_200_with_slots")
    @WithMockUser(roles = "USER")
    void user_authenticated_gets_200_with_slots() throws Exception {
        when(disponibilidadService.getDisponibilidad(any(LocalDate.class)))
                .thenReturn(new DisponibilidadResponse("2025-08-01",
                        List.of(new TramoDisponible("09:00", 60, 3))));

        mockMvc.perform(get("/api/reservas/disponibles").param("fecha", "2025-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2025-08-01"))
                .andExpect(jsonPath("$.tramosDisponibles[0].horaInicio").value("09:00"))
                .andExpect(jsonPath("$.tramosDisponibles[0].duracionMinutos").value(60))
                .andExpect(jsonPath("$.tramosDisponibles[0].plazasLibres").value(3));
    }

    // 4.2 — ADMIN autenticado → 200
    @Test
    @DisplayName("4.2 admin_authenticated_gets_200")
    @WithMockUser(roles = "ADMIN")
    void admin_authenticated_gets_200() throws Exception {
        when(disponibilidadService.getDisponibilidad(any(LocalDate.class)))
                .thenReturn(new DisponibilidadResponse("2025-08-01", List.of()));

        mockMvc.perform(get("/api/reservas/disponibles").param("fecha", "2025-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2025-08-01"));
    }

    // 4.3 — Sin Authorization → 401
    @Test
    @DisplayName("4.3 anonymous_gets_401")
    void anonymous_gets_401() throws Exception {
        mockMvc.perform(get("/api/reservas/disponibles").param("fecha", "2025-08-01"))
                .andExpect(status().isUnauthorized());
    }

    // 4.4 — JWT expirado/ inválido → 401 (el filtro limpia el contexto)
    @Test
    @DisplayName("4.4 expired_or_invalid_jwt_gets_401")
    void expired_or_invalid_jwt_gets_401() throws Exception {
        // A malformed/expired bearer token is rejected by JwtAuthFilter, leaving the request
        // unauthenticated; the security entry point returns 401 for this protected route.
        mockMvc.perform(get("/api/reservas/disponibles")
                        .param("fecha", "2025-08-01")
                        .header("Authorization", "Bearer expired.invalid.token"))
                .andExpect(status().isUnauthorized());
    }

    // 4.5 — fecha formato inválido → 400 VALIDATION_ERROR
    @Test
    @DisplayName("4.5 invalid_date_format_gets_400_validation_error")
    @WithMockUser(roles = "USER")
    void invalid_date_format_gets_400_validation_error() throws Exception {
        mockMvc.perform(get("/api/reservas/disponibles").param("fecha", "15-06-2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // 4.6 — sin parámetro fecha → 400 VALIDATION_ERROR
    @Test
    @DisplayName("4.6 missing_date_param_gets_400_validation_error")
    @WithMockUser(roles = "USER")
    void missing_date_param_gets_400_validation_error() throws Exception {
        mockMvc.perform(get("/api/reservas/disponibles"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // 4.7 — MANTENIMIENTO → 200 con tramosDisponibles: []
    @Test
    @DisplayName("4.7 maintenance_returns_200_with_empty_list")
    @WithMockUser(roles = "USER")
    void maintenance_returns_200_with_empty_list() throws Exception {
        when(disponibilidadService.getDisponibilidad(any(LocalDate.class)))
                .thenReturn(new DisponibilidadResponse("2025-09-01", List.of()));

        mockMvc.perform(get("/api/reservas/disponibles").param("fecha", "2025-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramosDisponibles").isArray())
                .andExpect(jsonPath("$.tramosDisponibles").isEmpty());
    }
}
