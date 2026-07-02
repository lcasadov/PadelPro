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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UsuariosMeController (GET/PATCH /api/usuarios/me).
 *
 * <p>Extends {@link PostgresIntegrationTest} so it runs both locally (external
 * Postgres on :5433) and in CI (Testcontainers).
 */
class UsuariosMeControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        testUser = new User(
                "me.user",
                passwordEncoder.encode("Password1"),
                "Me",
                "User",
                "me.user@example.com",
                UserRole.USER,
                UserStatus.ACTIVE,
                now,
                now
        );
        testUser = userRepository.saveAndFlush(testUser);
        userToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("GET /api/usuarios/me with valid JWT returns 200")
    void get_me_with_valid_jwt_returns_200() throws Exception {
        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is("me.user")))
                .andExpect(jsonPath("$.email", is("me.user@example.com")))
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("GET /api/usuarios/me without JWT returns 401")
    void get_me_without_jwt_returns_401() throws Exception {
        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/usuarios/me with valid fields returns 200")
    void patch_me_with_valid_fields_returns_200() throws Exception {
        Map<String, String> body = Map.of(
                "firstName", "Updated",
                "lastName", "Name",
                "phone", "+34611000001"
        );
        mockMvc.perform(patch("/api/usuarios/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Updated")))
                .andExpect(jsonPath("$.lastName", is("Name")))
                .andExpect(jsonPath("$.phone", is("+34611000001")));
    }

    @Test
    @DisplayName("PATCH /api/usuarios/me with duplicate email returns 409")
    void patch_me_with_duplicate_email_returns_409() throws Exception {
        // Create a second user with a different email
        OffsetDateTime now = OffsetDateTime.now();
        User other = new User(
                "other.user",
                passwordEncoder.encode("Password1"),
                "Other",
                "User",
                "other@example.com",
                UserRole.USER,
                UserStatus.ACTIVE,
                now,
                now
        );
        userRepository.saveAndFlush(other);

        Map<String, String> body = Map.of("email", "other@example.com");
        mockMvc.perform(patch("/api/usuarios/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("USUARIOS_EMAIL_CONFLICT")));
    }

    // -------------------------------------------------------------------------
    // POST /api/usuarios/me/password (D9 — forced/own password change)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/usuarios/me/password valid → 204, hash changes and flag cleared")
    void change_password_valid_returns_204_and_clears_flag() throws Exception {
        // Simulate a prior admin reset
        testUser.setMustChangePassword(true);
        userRepository.saveAndFlush(testUser);

        Map<String, String> body = Map.of(
                "current_password", "Password1",
                "new_password", "BrandNew2");
        mockMvc.perform(post("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());

        User reloaded = userRepository.findByLogin("me.user").orElseThrow();
        assert !reloaded.isMustChangePassword();
        assert passwordEncoder.matches("BrandNew2", reloaded.getPasswordHash());
        assert !passwordEncoder.matches("Password1", reloaded.getPasswordHash());
    }

    @Test
    @DisplayName("POST /api/usuarios/me/password wrong current password → 401")
    void change_password_wrong_current_returns_401() throws Exception {
        Map<String, String> body = Map.of(
                "current_password", "WrongOne1",
                "new_password", "BrandNew2");
        mockMvc.perform(post("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("AUTH_INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("POST /api/usuarios/me/password weak new password → 400 INVALID_PASSWORD")
    void change_password_weak_new_returns_400() throws Exception {
        Map<String, String> body = Map.of(
                "current_password", "Password1",
                "new_password", "weak");
        mockMvc.perform(post("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("INVALID_PASSWORD")));
    }

    @Test
    @DisplayName("POST /api/usuarios/me/password without JWT → 401")
    void change_password_without_jwt_returns_401() throws Exception {
        Map<String, String> body = Map.of(
                "current_password", "Password1",
                "new_password", "BrandNew2");
        mockMvc.perform(post("/api/usuarios/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/usuarios/me with role in body returns 200 but role does not change")
    void patch_me_with_role_in_body_role_does_not_change() throws Exception {
        // UpdateMyProfileCommand has no 'role' field — Jackson ignores unknown fields by default
        String body = "{\"firstName\":\"RoleTest\",\"role\":\"ADMIN\"}";
        mockMvc.perform(patch("/api/usuarios/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("USER")))
                .andExpect(jsonPath("$.firstName", is("RoleTest")));
    }
}
