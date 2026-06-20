package com.padelpro.auth.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.dto.SystemConfigResponse;
import com.padelpro.auth.application.dto.UpdateSystemConfigRequest;
import com.padelpro.auth.application.service.SystemConfigService;
import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.auth.domain.model.SystemConfig.PaymentGateway;
import com.padelpro.auth.domain.model.SystemConfig.PistaState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-027 — E2E Tests for AdminSystemConfigController with Spring Security + MockMvc.
 *
 * <p>Phase 2: HTTP-level tests with @SpringBootTest + @WithMockUser + Spring Security assertions.
 * Tests RBAC (401 anon, 403 insufficient role), validation errors (400), and response structure.
 * Verifies that secrets (PAN/CVV, tokens) are never exposed in responses.
 *
 * <p>8 scenarios covering:
 * - E2E-1: ADMIN can GET config (200)
 * - E2E-2: USER cannot GET config (403 FORBIDDEN)
 * - E2E-3: Anonymous cannot GET config (401 UNAUTHORIZED)
 * - E2E-4: ADMIN can PATCH config (200)
 * - E2E-5: USER cannot PATCH config (403 FORBIDDEN)
 * - E2E-6: Anonymous cannot PATCH config (401 UNAUTHORIZED)
 * - E2E-7: PATCH with validation failure (400 VALIDATION_ERROR)
 * - E2E-8: Responses never expose secrets (boolean flags only)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("E2E Tests — AdminSystemConfigController con Spring Security")
class AdminConfigE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SystemConfigService configService;

    // =========================================================================
    // E2E-1: GET /api/admin/sistema/config — ADMIN accede (200)
    // =========================================================================

    @Test
    @DisplayName("E2E-1: admin_can_access_config_endpoint_and_receives_200")
    @WithMockUser(roles = "ADMIN")
    void admin_can_access_config_endpoint_and_receives_200() throws Exception {
        // Arrange
        SystemConfigResponse mockResponse = new SystemConfigResponse(
                "Club Test",
                "Description",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                4,
                new java.math.BigDecimal("15.00"),
                2,
                false,
                false,
                OffsetDateTime.now()
        );
        when(configService.getConfig()).thenReturn(mockResponse);

        // Act
        ResultActions result = mockMvc.perform(get("/api/admin/sistema/config")
                .header("Accept", "application/json"));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.clubName").value("Club Test"))
              .andExpect(jsonPath("$.clubDescription").value("Description"))
              .andExpect(jsonPath("$.paymentGateway").value("CASH"))
              .andExpect(jsonPath("$.pistaState").value("ACTIVA"))
              .andExpect(jsonPath("$.maxParticipantsPerPista").value(4))
              .andExpect(jsonPath("$.telegramBotConfigured").isBoolean())
              .andExpect(jsonPath("$.redsysConfigured").isBoolean());
    }

    // =========================================================================
    // E2E-2: GET /api/admin/sistema/config — USER accede (403 FORBIDDEN)
    // =========================================================================

    @Test
    @DisplayName("E2E-2: user_role_receives_403_forbidden_on_config_endpoint")
    @WithMockUser(roles = "USER")
    void user_role_receives_403_forbidden_on_config_endpoint() throws Exception {
        // Act
        ResultActions result = mockMvc.perform(get("/api/admin/sistema/config")
                .header("Accept", "application/json"));

        // Assert
        result.andExpect(status().isForbidden());
    }

    // =========================================================================
    // E2E-3: GET /api/admin/sistema/config — NO AUTENTICADO (401 UNAUTHORIZED)
    // =========================================================================

    @Test
    @DisplayName("E2E-3: unauthenticated_user_receives_401_unauthorized_on_config_endpoint")
    void unauthenticated_user_receives_401_unauthorized_on_config_endpoint() throws Exception {
        // Act — sin @WithMockUser
        ResultActions result = mockMvc.perform(get("/api/admin/sistema/config")
                .header("Accept", "application/json"));

        // Assert
        result.andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // E2E-4: PATCH /api/admin/sistema/config — ADMIN actualiza (200)
    // =========================================================================

    @Test
    @DisplayName("E2E-4: admin_can_update_config_via_patch_and_receives_200")
    @WithMockUser(roles = "ADMIN")
    void admin_can_update_config_via_patch_and_receives_200() throws Exception {
        // Arrange
        SystemConfigResponse mockResponse = new SystemConfigResponse(
                "Updated Club",
                "Updated Description",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                6,
                new java.math.BigDecimal("15.00"),
                2,
                false,
                false,
                OffsetDateTime.now()
        );
        when(configService.updateConfig(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockResponse);

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Updated Club",
                "Updated Description",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                null,
                null,
                null,
                6,
                null,
                null
        );

        // Act
        ResultActions result = mockMvc.perform(patch("/api/admin/sistema/config")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Assert
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.clubName").value("Updated Club"))
              .andExpect(jsonPath("$.clubDescription").value("Updated Description"))
              .andExpect(jsonPath("$.maxParticipantsPerPista").value(6));
    }

    // =========================================================================
    // E2E-5: PATCH /api/admin/sistema/config — USER intenta actualizar (403)
    // =========================================================================

    @Test
    @DisplayName("E2E-5: user_role_receives_403_forbidden_on_config_update_patch")
    @WithMockUser(roles = "USER")
    void user_role_receives_403_forbidden_on_config_update_patch() throws Exception {
        // Arrange
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club",
                "D",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                null,
                null,
                null,
                4,
                null,
                null
        );

        // Act
        ResultActions result = mockMvc.perform(patch("/api/admin/sistema/config")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Assert
        result.andExpect(status().isForbidden());
    }

    // =========================================================================
    // E2E-6: PATCH /api/admin/sistema/config — NO AUTENTICADO (401)
    // =========================================================================

    @Test
    @DisplayName("E2E-6: unauthenticated_user_receives_401_unauthorized_on_config_update_patch")
    void unauthenticated_user_receives_401_unauthorized_on_config_update_patch() throws Exception {
        // Arrange
        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club",
                "D",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                null,
                null,
                null,
                4,
                null,
                null
        );

        // Act — sin @WithMockUser
        ResultActions result = mockMvc.perform(patch("/api/admin/sistema/config")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Assert
        result.andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // E2E-7: PATCH con validación (REDSYS sin credenciales — 400 VALIDATION_ERROR)
    // =========================================================================

    @Test
    @DisplayName("E2E-7: patch_with_redsys_missing_merchant_key_returns_400_validation_error")
    @WithMockUser(roles = "ADMIN")
    void patch_with_redsys_missing_merchant_key_returns_400_validation_error() throws Exception {
        // Arrange
        when(configService.updateConfig(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ValidationException("merchant_key is required for REDSYS payment gateway"));

        UpdateSystemConfigRequest request = new UpdateSystemConfigRequest(
                "Club",
                "D",
                PistaState.ACTIVA,
                PaymentGateway.REDSYS,
                "merchant-id",
                null, // MISSING merchant_key
                null,
                4,
                null,
                null
        );

        // Act
        ResultActions result = mockMvc.perform(patch("/api/admin/sistema/config")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Assert
        result.andExpect(status().isBadRequest());
    }

    // =========================================================================
    // E2E-8: Respuesta nunca expone secretos (boolean flags only)
    // =========================================================================

    @Test
    @DisplayName("E2E-8: response_never_exposes_secrets_only_boolean_flags")
    @WithMockUser(roles = "ADMIN")
    void response_never_exposes_secrets_only_boolean_flags() throws Exception {
        // Arrange
        SystemConfigResponse mockResponse = new SystemConfigResponse(
                "Club",
                "D",
                PistaState.ACTIVA,
                PaymentGateway.CASH,
                4,
                new java.math.BigDecimal("15.00"),
                2,
                true,
                true,
                OffsetDateTime.now()
        );
        when(configService.getConfig()).thenReturn(mockResponse);

        // Act
        ResultActions result = mockMvc.perform(get("/api/admin/sistema/config")
                .header("Accept", "application/json"));

        // Assert
        // telegramBotConfigured and redsysConfigured are booleans (true/false)
        result.andExpect(status().isOk())
              .andExpect(jsonPath("$.telegramBotConfigured").isBoolean())
              .andExpect(jsonPath("$.redsysConfigured").isBoolean())
              // Verify that secret fields do NOT exist in response
              .andExpect(jsonPath("$.telegramBotToken").doesNotExist())
              .andExpect(jsonPath("$.redsysMerchantKey").doesNotExist())
              .andExpect(jsonPath("$.redsysMerchantId").doesNotExist());
    }
}
