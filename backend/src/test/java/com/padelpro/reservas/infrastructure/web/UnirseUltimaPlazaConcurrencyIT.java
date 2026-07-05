package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for the atomic last-seat join (capability partidas, D2, RN-RES-02).
 *
 * <p>Creates a reservation with only ONE free seat (owner + 2 registered, max_participants = 4 leaves
 * room for 1 more... so we fill to 3/4) and fires N simultaneous joins by N distinct users. Exactly
 * one must win with 200 and the rest lose with 422 — guaranteed by loading the reservation row
 * {@code FOR UPDATE} so the seat check + insert serialise. Real HTTP + thread pool (never MockMvc,
 * whose transaction/security wiring is not thread-safe).
 */
@DisplayName("IT — unión concurrente a la última plaza (Postgres real)")
class UnirseUltimaPlazaConcurrencyIT extends PostgresIntegrationTest {

    private static final int RACERS = 8;

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private final LocalDate futureDate = LocalDate.now().plusDays(7);
    private String ownerToken;
    private String[] racerTokens;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now();
        User owner = userRepository.saveAndFlush(new User("owner.race", passwordEncoder.encode("Password1"),
                "Owner", "Race", "owner.race@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        ownerToken = jwtService.generateAccessToken(owner);
        racerTokens = new String[RACERS];
        for (int i = 0; i < RACERS; i++) {
            User u = userRepository.saveAndFlush(new User("joinrace" + i, passwordEncoder.encode("Password1"),
                    "Joiner", "R" + i, "joinrace" + i + "@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
            racerTokens[i] = jwtService.generateAccessToken(u);
        }
    }

    private String createReservaWithOneFreeSeat() throws Exception {
        // owner + 2 registered guests → 3/4 occupied, exactly ONE seat left.
        OffsetDateTime now = OffsetDateTime.now();
        User g2 = userRepository.saveAndFlush(new User("guest2", passwordEncoder.encode("Password1"),
                "Guest", "Two", "guest2@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        User g3 = userRepository.saveAndFlush(new User("guest3", passwordEncoder.encode("Password1"),
                "Guest", "Three", "guest3@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "race", List.of(
                        new CrearReservaRequest.ParticipanteAdicional(g2.getId(), null, null),
                        new CrearReservaRequest.ParticipanteAdicional(g3.getId(), null, null))));
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/reservas")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("2.2 N uniones simultáneas a la última plaza → exactamente 1×200 y (N-1)×422")
    void should_admit_exactly_one_to_the_last_seat() throws Exception {
        String reservaId = createReservaWithOneFreeSeat();
        String url = "http://localhost:" + port + "/api/reservas/" + reservaId + "/unirse";

        HttpClient client = HttpClient.newHttpClient();
        ExecutorService executor = Executors.newFixedThreadPool(RACERS);
        CountDownLatch ready = new CountDownLatch(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger unprocessable = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            final String token = racerTokens[i];
            futures.add(executor.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
                ready.countDown();
                start.await();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 200) {
                    ok.incrementAndGet();
                } else if (code == 422) {
                    unprocessable.incrementAndGet();
                } else {
                    other.incrementAndGet();
                }
                return code;
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        for (Future<Integer> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(other.get()).as("unexpected status codes (neither 200 nor 422)").isZero();
        assertThat(ok.get()).as("exactly one 200").isEqualTo(1);
        assertThat(unprocessable.get()).as("the rest are 422").isEqualTo(RACERS - 1);

        // DB invariant: the reservation ended up with exactly max_participants (4) participants.
        var r = reservationRepository.findByIdWithParticipants(UUID.fromString(reservaId)).orElseThrow();
        assertThat(r.getParticipants()).hasSize(4);
    }
}
