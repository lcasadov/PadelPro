package com.padelpro.reservas.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.JwtService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency / anti-overlap integration test (task 8.3, RN-RES-01, D1, critical flow #4).
 *
 * <p>Fires {@value #THREAD_COUNT} simultaneous {@code POST /api/reservas} for the SAME slot (each by
 * a different authenticated user) and asserts that exactly one wins with 201 and every other loses
 * with 409 — guaranteed by the PostgreSQL {@code excl_res_no_overlap} gist exclusion constraint
 * (V7), which has no H2 equivalent. Therefore this test runs only against a real PostgreSQL via
 * Testcontainers.
 *
 * <p>Real concurrency is achieved with a running server ({@code RANDOM_PORT}) plus a thread pool and
 * a {@link CountDownLatch} so all requests are released together — never MockMvc, whose
 * SecurityContext/transaction wiring is not thread-safe and would not exercise the DB race.
 *
 * <p>Datasource (external Vía A or Testcontainers) is wired by {@link PostgresIntegrationTest}.
 */
@DisplayName("IT — anti-solapamiento concurrente (Postgres real, constraint gist)")
class ReservaSolapamientoConcurrencyIT extends PostgresIntegrationTest {

    private static final int THREAD_COUNT = 10;

    @LocalServerPort private int port;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ReservationJpaRepository reservationRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private final LocalDate futureDate = LocalDate.now().plusDays(7);
    private String[] tokens;

    @BeforeEach
    void setUp() {
        tokens = new String[THREAD_COUNT];
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < THREAD_COUNT; i++) {
            User u = userRepository.saveAndFlush(new User(
                    "racer" + i, passwordEncoder.encode("Password1"), "Racer", "User" + i,
                    "racer" + i + "@example.com", UserRole.USER, UserStatus.ACTIVE, now, now));
            tokens[i] = jwtService.generateAccessToken(u);
        }
    }

    @Test
    @DisplayName("8.3 N POST simultáneos a la misma franja → exactamente 1×201 y (N-1)×409")
    void should_have_exactly_one_success_and_rest_conflict_under_concurrency() throws Exception {
        String body = objectMapper.writeValueAsString(new CrearReservaRequest(
                futureDate.toString(), "18:00", 60, "race", null));
        String url = "http://localhost:" + port + "/api/reservas";

        HttpClient client = HttpClient.newHttpClient();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            final String token = tokens[i];
            futures.add(executor.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                ready.countDown();
                start.await();                 // release all threads at the same instant
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 201) {
                    created.incrementAndGet();
                } else if (code == 409) {
                    conflict.incrementAndGet();
                } else {
                    other.incrementAndGet();
                }
                return code;
            }));
        }

        ready.await(10, TimeUnit.SECONDS);     // wait until all threads are armed
        start.countDown();                     // fire
        for (Future<Integer> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly one winner, the rest are clean 409s — no 500s, no extra 201s.
        assertThat(other.get()).as("unexpected status codes (neither 201 nor 409)").isZero();
        assertThat(created.get()).as("exactly one 201").isEqualTo(1);
        assertThat(conflict.get()).as("the rest are 409").isEqualTo(THREAD_COUNT - 1);

        // DB invariant: a single active reservation + its single payment for that slot.
        long active = reservationRepository.findAll().stream()
                .filter(r -> r.getStatus().name().equals("PENDING_CONFIRMATION"))
                .count();
        assertThat(active).isEqualTo(1);
        assertThat(paymentRepository.findAll()).hasSize(1);
    }
}
