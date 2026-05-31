package com.padelpro.usuarios.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
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
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UsuariosMeController (GET/PATCH /api/usuarios/me).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UsuariosMeControllerIntegrationTest {

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
