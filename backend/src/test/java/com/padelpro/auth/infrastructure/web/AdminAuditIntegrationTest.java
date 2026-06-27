package com.padelpro.auth.infrastructure.web;

import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.AuditLogRepository;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AdminAuditController (/api/admin/audit).
 *
 * <p>Uses MockMvc against a real PostgreSQL. Cleanup is performed before
 * each test via /sql/cleanup.sql.
 *
 * <p>Extends {@link PostgresIntegrationTest} so it runs both locally (external
 * Postgres on :5433) and in CI (Testcontainers).
 */
class AdminAuditIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        adminUser = userRepository.saveAndFlush(new User(
                "audit.admin", passwordEncoder.encode("Password1"),
                "Audit", "Admin", "audit.admin@example.com",
                UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        regularUser = userRepository.saveAndFlush(new User(
                "audit.user", passwordEncoder.encode("Password1"),
                "Audit", "User", "audit.user@example.com",
                UserRole.USER, UserStatus.ACTIVE, now, now));

        adminToken = jwtService.generateAccessToken(adminUser);
        userToken  = jwtService.generateAccessToken(regularUser);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private AuditLog saveLog(String action, User user, String details) {
        // Use the JpaRepository saveAndFlush (bypassing the default stub)
        AuditLog log = new AuditLog(action, user, "127.0.0.1", details, OffsetDateTime.now());
        return ((org.springframework.data.jpa.repository.JpaRepository<AuditLog, Long>)
                auditLogRepository).saveAndFlush(log);
    }

    // =========================================================================
    // 1. Admin lists paginated audit log
    // =========================================================================

    @Test
    @DisplayName("admin_can_list_audit_log_paginated")
    void admin_can_list_audit_log_paginated() throws Exception {
        saveLog("LOGIN_SUCCESS", adminUser, "ok");

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // =========================================================================
    // 2. Filter by action
    // =========================================================================

    @Test
    @DisplayName("admin_can_filter_by_action")
    void admin_can_filter_by_action() throws Exception {
        saveLog("ACCESS_DENIED", adminUser, "forbidden");
        saveLog("LOGIN_SUCCESS", adminUser, "ok");

        mockMvc.perform(get("/api/admin/audit")
                        .param("action", "ACCESS_DENIED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("ACCESS_DENIED")))
                .andExpect(jsonPath("$.content[*].action", not(hasItem("LOGIN_SUCCESS"))));
    }

    // =========================================================================
    // 3. Filter by userId
    // =========================================================================

    @Test
    @DisplayName("admin_can_filter_by_user_id")
    void admin_can_filter_by_user_id() throws Exception {
        saveLog("LOGIN_SUCCESS", adminUser, "admin login");
        saveLog("LOGIN_SUCCESS", regularUser, "user login");

        mockMvc.perform(get("/api/admin/audit")
                        .param("userId", regularUser.getId().toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(regularUser.getId()));
    }

    // =========================================================================
    // 4. Filter by date range
    // =========================================================================

    @Test
    @DisplayName("admin_can_filter_by_date_range")
    void admin_can_filter_by_date_range() throws Exception {
        saveLog("LOGIN_SUCCESS", adminUser, "ok");

        String from = OffsetDateTime.now().minusMinutes(5).toString();
        String to   = OffsetDateTime.now().plusMinutes(5).toString();

        mockMvc.perform(get("/api/admin/audit")
                        .param("from", from)
                        .param("to", to)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // =========================================================================
    // 5. size > 100 → 400 VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("size_exceeding_100_returns_400_validation_error")
    void size_exceeding_100_returns_400_validation_error() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .param("size", "500")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // 6. USER role → 403 ACCESS_DENIED
    // =========================================================================

    @Test
    @DisplayName("user_role_receives_403_on_audit_endpoint")
    void user_role_receives_403_on_audit_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 7. Unauthenticated → 401
    // =========================================================================

    @Test
    @DisplayName("unauthenticated_request_receives_401_on_audit_endpoint")
    void unauthenticated_request_receives_401_on_audit_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 8. Response does not expose sensitive fields in details
    // =========================================================================

    @Test
    @DisplayName("response_does_not_contain_sensitive_fields")
    void response_does_not_contain_sensitive_fields() throws Exception {
        saveLog("LOGIN_SUCCESS", adminUser, "user logged in successfully");

        String responseBody = mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify response body does not contain sensitive tokens or credentials
        org.assertj.core.api.Assertions.assertThat(responseBody.toLowerCase())
                .doesNotContain("password_hash")
                .doesNotContain("\"token\"")
                .doesNotContain("\"otp\"")
                .doesNotContain("\"secret\"");
    }
}
