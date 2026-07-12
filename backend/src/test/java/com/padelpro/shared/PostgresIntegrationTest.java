package com.padelpro.shared;

import com.padelpro.auth.infrastructure.web.filter.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for reservas Spring integration tests that need a real PostgreSQL 15 (the
 * {@code excl_res_no_overlap} gist constraint and the {@code reservation_status} enum cast have no
 * H2 equivalent, see TESTING-STRATEGY §3.3).
 *
 * <p><b>Two ways to provide PostgreSQL</b>, mirroring {@code ReservationsMigrationIT} (Vía A vs
 * Testcontainers) so the same tests run both locally and in CI:
 * <ul>
 *   <li><b>External (Vía A):</b> pass {@code -Dit.postgres.url=jdbc:postgresql://localhost:5433/padelpro_it
 *       -Dit.postgres.user=padelpro -Dit.postgres.password=padelpro_dev}. The test connects over plain
 *       JDBC/TCP, bypassing docker-java entirely — required on hosts where the Docker Desktop named
 *       pipe breaks the Testcontainers client.</li>
 *   <li><b>Testcontainers (CI default):</b> if {@code it.postgres.url} is absent, an ephemeral
 *       {@code postgres:15-alpine} container is started and its JDBC URL injected.</li>
 * </ul>
 *
 * <p>Flyway runs with {@code clean-on-validation-error} (it profile) so V1..V9 are applied to the
 * target database; {@code @Sql cleanup.sql} isolates each test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class PostgresIntegrationTest {

    private static final boolean USE_EXTERNAL;
    private static final String JDBC_URL;
    private static final String USERNAME;
    private static final String PASSWORD;
    private static final PostgreSQLContainer<?> CONTAINER;

    static {
        String externalUrl = System.getProperty("it.postgres.url");
        if (externalUrl != null && !externalUrl.isBlank()) {
            USE_EXTERNAL = true;
            CONTAINER = null;
            JDBC_URL = externalUrl;
            USERNAME = System.getProperty("it.postgres.user", "padelpro");
            PASSWORD = System.getProperty("it.postgres.password", "padelpro_dev");
        } else {
            USE_EXTERNAL = false;
            CONTAINER = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("padelpro_test")
                    .withUsername("padelpro_test")
                    .withPassword("padelpro_test");
            CONTAINER.start();
            JDBC_URL = CONTAINER.getJdbcUrl();
            USERNAME = CONTAINER.getUsername();
            PASSWORD = CONTAINER.getPassword();
        }
    }

    /**
     * The {@link RateLimitFilter} bucket store is a singleton in the cached Spring context, so its
     * per-IP counters leak across integration test classes and methods and cause order-dependent
     * failures (notably {@code RateLimitIntegrationTest} intermittently seeing 401 where it expects
     * 429). Optional ({@code required = false}) so tests running with a slice context that does not
     * register the filter still load. See {@link RateLimitFilter#resetBuckets()}.
     */
    @Autowired(required = false)
    private RateLimitFilter rateLimitFilter;

    /** Reset rate-limit buckets before every test method so each starts with full capacity. */
    @BeforeEach
    void resetRateLimitBuckets() {
        if (rateLimitFilter != null) {
            rateLimitFilter.resetBuckets();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    /** True when connected to an externally-provided PostgreSQL (Vía A) instead of Testcontainers. */
    protected static boolean usingExternalPostgres() {
        return USE_EXTERNAL;
    }

    /** Server port for tests that need real HTTP (concurrency). Read via {@code @LocalServerPort} too. */
    protected static String jdbcUrl() {
        return JDBC_URL;
    }
}
