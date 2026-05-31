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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED — E2E tests for security (TestRestTemplate, full HTTP stack).
 * These tests FAIL until the production components are implemented (GREEN phase).
 *
 * Tests cover:
 *  - Full cycle: user deactivated mid-session → JWT still valid → 403 with JSON
 *  - Role escalation via PATCH /api/usuarios/me is silently ignored
 *  - Audit log accumulates denied accesses
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("it")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SecurityE2ETest {

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

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private User activeUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        activeUser = userRepository.saveAndFlush(new User(
                "e2e.user", passwordEncoder.encode("Password1"),
                "E2E", "User", "e2e@example.com",
                UserRole.USER, UserStatus.ACTIVE, now, now));

        adminUser = userRepository.saveAndFlush(new User(
                "e2e.admin", passwordEncoder.encode("Password1"),
                "E2E", "Admin", "e2eadmin@example.com",
                UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        userToken = jwtService.generateAccessToken(activeUser);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // =========================================================================
    // Test 1: User deactivated mid-session → 403 ACCOUNT_NOT_ACTIVE
    // =========================================================================

    @Test
    @DisplayName("inactive_user_full_cycle_e2e")
    void inactive_user_full_cycle_e2e() {
        // Step 1: User is ACTIVE, JWT obtained in setUp()
        // Verify user can access their profile
        ResponseEntity<Map> beforeDeactivation = restTemplate.exchange(
                "/api/usuarios/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );
        assertThat(beforeDeactivation.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Step 2: Admin deactivates the user
        activeUser.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(activeUser);

        // Step 3: User's JWT is still valid but account is now INACTIVE
        ResponseEntity<Map> afterDeactivation = restTemplate.exchange(
                "/api/usuarios/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        assertThat(afterDeactivation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(afterDeactivation.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.toString()).contains("application/json"));
        assertThat(afterDeactivation.getBody())
                .isNotNull()
                .containsEntry("error", "ACCOUNT_NOT_ACTIVE");
    }

    // =========================================================================
    // Test 2: Role escalation via PATCH /api/usuarios/me is silently ignored
    // =========================================================================

    @Test
    @DisplayName("role_escalation_attempt_via_patch_me_e2e")
    void role_escalation_attempt_via_patch_me_e2e() {
        HttpHeaders headers = bearerHeaders(userToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Attempt to escalate role via PATCH /api/usuarios/me
        Map<String, String> patchBody = Map.of(
                "role", "ADMIN",
                "firstName", "Hacker"
        );

        ResponseEntity<Map> patchResponse = restTemplate.exchange(
                "/api/usuarios/me",
                HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers),
                Map.class
        );

        // Should succeed (200) but role must NOT change
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify role is still USER
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/api/usuarios/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );
        assertThat(getResponse.getBody())
                .isNotNull()
                .containsEntry("role", "USER");
    }

    // =========================================================================
    // Test 3: Audit log accumulates denied accesses
    // =========================================================================

    @Test
    @DisplayName("audit_log_contains_denied_accesses_e2e")
    void audit_log_contains_denied_accesses_e2e() {
        // USER role tries to access admin endpoint twice
        restTemplate.exchange(
                "/api/admin/usuarios",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );
        restTemplate.exchange(
                "/api/admin/usuarios",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(userToken)),
                Map.class
        );

        long deniedCount = auditLogRepository.findAll().stream()
                .filter(log -> "ACCESS_DENIED".equals(log.getAction()))
                .count();

        assertThat(deniedCount).isGreaterThanOrEqualTo(2);
    }
}
