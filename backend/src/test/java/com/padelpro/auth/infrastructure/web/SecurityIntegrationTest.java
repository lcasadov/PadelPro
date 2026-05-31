package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.AuditLogRepository;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TDD RED — Integration tests for security filters and handlers.
 * Tests cover:
 *  - UserStatusFilter: INACTIVE and PENDING users receive 403
 *  - CustomAccessDeniedHandler: USER role on /api/admin/** receives 403 with JSON body
 *  - CustomAuthenticationEntryPoint: unauthenticated requests receive 401 with JSON body
 *  - Audit log recording for denied accesses
 *  - Webhook routes not blocked by JWT filter
 *
 * These tests FAIL until the production components are implemented (GREEN phase).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("padelpro_test")
                    .withUsername("padelpro_test")
                    .withPassword("padelpro_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private User activeUser;
    private User adminUser;
    private String activeUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        activeUser = userRepository.saveAndFlush(new User(
                "regular.user", passwordEncoder.encode("Password1"),
                "Regular", "User", "regular@example.com",
                UserRole.USER, UserStatus.ACTIVE, now, now));

        adminUser = userRepository.saveAndFlush(new User(
                "admin.user", passwordEncoder.encode("Password1"),
                "Admin", "User", "admin@example.com",
                UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        activeUserToken = jwtService.generateAccessToken(activeUser);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    // =========================================================================
    // UserStatusFilter — INACTIVE / PENDING → 403
    // =========================================================================

    @Test
    @DisplayName("user_with_inactive_status_receives_403_on_authenticated_endpoint")
    void user_with_inactive_status_receives_403_on_authenticated_endpoint() throws Exception {
        // Deactivate the user in DB after token was issued
        activeUser.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(activeUser);

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + activeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("user_with_pending_status_receives_403_on_authenticated_endpoint")
    void user_with_pending_status_receives_403_on_authenticated_endpoint() throws Exception {
        // Change user to PENDING in DB after token was issued
        activeUser.setStatus(UserStatus.PENDING);
        userRepository.saveAndFlush(activeUser);

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + activeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_ACTIVE"));
    }

    // =========================================================================
    // CustomAccessDeniedHandler — USER on /api/admin/** → 403 with JSON
    // =========================================================================

    @Test
    @DisplayName("user_role_receives_403_on_admin_endpoint_with_json_body")
    void user_role_receives_403_on_admin_endpoint_with_json_body() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + activeUserToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    // =========================================================================
    // CustomAuthenticationEntryPoint — no token → 401 with JSON
    // =========================================================================

    @Test
    @DisplayName("unauthenticated_request_receives_401_with_json_body")
    void unauthenticated_request_receives_401_with_json_body() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("AUTH_REQUIRED"));
    }

    // =========================================================================
    // Admin user successfully accesses admin endpoint
    // =========================================================================

    @Test
    @DisplayName("admin_user_accesses_admin_endpoint_successfully")
    void admin_user_accesses_admin_endpoint_successfully() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Audit log — denied access is recorded
    // =========================================================================

    @Test
    @DisplayName("forbidden_access_is_recorded_in_audit_log")
    void forbidden_access_is_recorded_in_audit_log() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + activeUserToken))
                .andExpect(status().isForbidden());

        long deniedCount = auditLogRepository.findAll().stream()
                .filter(log -> "ACCESS_DENIED".equals(log.getAction()))
                .count();
        assertThat(deniedCount).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // Webhook routes — not blocked by JWT absence
    // =========================================================================

    @Test
    @DisplayName("webhook_routes_are_not_blocked_by_jwt_absence")
    void webhook_routes_are_not_blocked_by_jwt_absence() throws Exception {
        // POST to /api/bot/telegram without JWT — Spring Security must NOT return 401
        // (can be 404 if endpoint doesn't exist, or 405, but never 401)
        int status = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/bot/telegram")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
    }
}
