package com.padelpro.usuarios.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AdminUsuariosController (/api/admin/usuarios).
 *
 * <p>Extends {@link PostgresIntegrationTest} so it runs both locally (external
 * Postgres on :5433) and in CI (Testcontainers).
 */
class AdminUsuariosControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User adminUser;
    private User regularUser;
    private User pendingUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        adminUser = userRepository.saveAndFlush(new User(
                "admin.user", passwordEncoder.encode("Password1"),
                "Admin", "User", "admin@example.com",
                UserRole.ADMIN, UserStatus.ACTIVE, now, now));

        regularUser = userRepository.saveAndFlush(new User(
                "regular.user", passwordEncoder.encode("Password1"),
                "Regular", "User", "regular@example.com",
                UserRole.USER, UserStatus.ACTIVE, now, now));

        pendingUser = userRepository.saveAndFlush(new User(
                "pending.user", passwordEncoder.encode("Password1"),
                "Pending", "User", "pending@example.com",
                UserRole.USER, UserStatus.PENDING, now, now));

        adminToken = jwtService.generateAccessToken(adminUser);
        userToken  = jwtService.generateAccessToken(regularUser);
    }

    // -------------------------------------------------------------------------
    // POST /api/admin/usuarios
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/admin/usuarios valid → 201 status=ACTIVE")
    void create_user_valid_returns_201_active() throws Exception {
        Map<String, String> body = Map.of(
                "login", "new.user",
                "firstName", "New",
                "lastName", "User",
                "email", "newuser@example.com",
                "password", "Password1"
        );
        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.login", is("new.user")));
    }

    @Test
    @DisplayName("POST /api/admin/usuarios duplicate email → 409")
    void create_user_duplicate_email_returns_409() throws Exception {
        Map<String, String> body = Map.of(
                "login", "another.login",
                "firstName", "A",
                "lastName", "B",
                "email", "regular@example.com",   // already used by regularUser
                "password", "Password1"
        );
        mockMvc.perform(post("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("USUARIOS_EMAIL_CONFLICT")));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/admin/usuarios/{id}/aprobar
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /api/admin/usuarios/{id}/aprobar on PENDING → 200 status=ACTIVE")
    void approve_pending_user_returns_200_active() throws Exception {
        mockMvc.perform(patch("/api/admin/usuarios/{id}/aprobar", pendingUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("PATCH /api/admin/usuarios/{id}/aprobar on ACTIVE → 422")
    void approve_active_user_returns_422() throws Exception {
        mockMvc.perform(patch("/api/admin/usuarios/{id}/aprobar", regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("USUARIO_NOT_PENDING")));
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/usuarios?status=
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/admin/usuarios?status=PENDING → 200 contains only PENDING users")
    void list_users_filtered_by_pending() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/admin/usuarios/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/admin/usuarios/{id} → 204 and user becomes INACTIVE")
    void deactivate_user_returns_204() throws Exception {
        mockMvc.perform(delete("/api/admin/usuarios/{id}", regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify in DB
        User updated = userRepository.findByLogin(regularUser.getLogin()).orElseThrow();
        assert updated.getStatus() == UserStatus.INACTIVE;
    }

    @Test
    @DisplayName("DELETE /api/admin/usuarios/{self} → 422 (RN-AUTH-05)")
    void deactivate_self_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/usuarios/{id}", adminUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("ADMIN_SELF_DEACTIVATION")));
    }

    // -------------------------------------------------------------------------
    // Authorization checks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/admin/usuarios with role USER → 403")
    void list_users_with_user_role_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/usuarios/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/admin/usuarios/{id} non-existent → 404")
    void get_user_nonexistent_returns_404() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("USUARIO_NOT_FOUND")));
    }
}
