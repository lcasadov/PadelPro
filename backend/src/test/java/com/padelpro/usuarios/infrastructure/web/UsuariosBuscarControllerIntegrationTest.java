package com.padelpro.usuarios.infrastructure.web;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link UsuariosBuscarController} (GET /api/usuarios/buscar) — the
 * registered-partner search (D5, tasks 3.1/3.2).
 *
 * <p>Verifies: match by name and by email returns {@code [{id, nombre}]}; no matches → 200 [];
 * anonymous → 401; short term → []; and that no sensitive field (email, passwordHash, role,
 * status) is ever projected into the response.
 */
class UsuariosBuscarControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String userToken;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();

        // The caller (an authenticated USER).
        User caller = persist("caller", "Caller", "One", "caller@example.com",
                UserRole.USER, UserStatus.ACTIVE, now);
        userToken = jwtService.generateAccessToken(caller);

        // Searchable ACTIVE members.
        persist("ana.lopez", "Ana", "Lopez", "ana.lopez@club.com",
                UserRole.USER, UserStatus.ACTIVE, now);
        persist("juan.perez", "Juan", "Perez", "juan.perez@mail.com",
                UserRole.USER, UserStatus.ACTIVE, now);
    }

    private User persist(String login, String firstName, String lastName, String email,
                         UserRole role, UserStatus status, OffsetDateTime now) {
        User u = new User(login, passwordEncoder.encode("Password1"),
                firstName, lastName, email, role, status, now, now);
        return userRepository.saveAndFlush(u);
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar by name returns 200 with [{id, nombre}]")
    void search_by_name_returns_id_and_nombre() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "ana")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].nombre", is("Ana Lopez")));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar by email matches but never returns the email")
    void search_by_email_matches_without_exposing_email() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "juan.perez@mail")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].nombre", is("Juan Perez")))
                // No sensitive fields leaked: only id + nombre keys are present.
                .andExpect(jsonPath("$[0]", not(hasKey("email"))))
                .andExpect(jsonPath("$[0]", not(hasKey("passwordHash"))))
                .andExpect(jsonPath("$[0]", not(hasKey("role"))))
                .andExpect(jsonPath("$[0]", not(hasKey("status"))));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar every result exposes only id and nombre")
    void search_results_expose_only_id_and_nombre() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "an")          // matches "Ana Lopez" and "Juan Perez"
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[*]", everyItem(hasKey("id"))))
                .andExpect(jsonPath("$[*]", everyItem(hasKey("nombre"))))
                .andExpect(jsonPath("$[*]", everyItem(not(hasKey("email")))));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar with no matches returns 200 []")
    void search_no_matches_returns_empty_array() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "zzzznotexist")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar with a short term returns 200 []")
    void search_short_term_returns_empty_array() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "a")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar without JWT returns 401")
    void search_without_jwt_returns_401() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "ana"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar excludes non-ACTIVE (PENDING/INACTIVE) users")
    void search_excludes_non_active_users() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        // Same searchable name, but neither is ACTIVE → must not appear.
        persist("ana.pending", "Ana", "Pending", "ana.pending@club.com",
                UserRole.USER, UserStatus.PENDING, now);
        persist("ana.inactive", "Ana", "Inactive", "ana.inactive@club.com",
                UserRole.USER, UserStatus.INACTIVE, now);

        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "ana")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                // Only the ACTIVE "Ana Lopez" from setUp() matches.
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].nombre", is("Ana Lopez")));
    }

    @Test
    @DisplayName("GET /api/usuarios/buscar with a '%' wildcard term does not dump the directory")
    void search_percent_wildcard_does_not_return_all() throws Exception {
        // "a%" (length ≥ MIN_TERM_LENGTH so it reaches the query): escapeLike turns the '%' into '\%',
        // and the explicit ESCAPE '\' makes the DB treat it as a literal percent sign. No member has a
        // literal '%' in their name/email → empty result, i.e. the wildcard cannot dump the directory.
        mockMvc.perform(get("/api/usuarios/buscar")
                        .param("q", "a%")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
