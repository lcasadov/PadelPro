package com.padelpro.auth.infrastructure.config;

import com.padelpro.auth.application.service.SeedUsersService;
import com.padelpro.auth.domain.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provisions the shared test users on application start when {@code SEED_USERS_ENABLED=true}.
 *
 * <p>The flag is set in {@code docker-compose.yml} for the deployed stack, so the users are created
 * in the real (EC2) database on deploy. Unit/integration tests run against a Spring test context
 * (Testcontainers), which does not set the flag, so this runner stays inert there. Credentials come
 * from environment variables with defaults matching {@code RESUME.md}; the runner never logs them
 * (RN-RGPD-04), only whether each user was created.
 */
@Component
@ConditionalOnProperty(name = "SEED_USERS_ENABLED", havingValue = "true")
public class SeedUsersRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUsersRunner.class);

    private final SeedUsersService seedUsersService;
    private final String adminEmail;
    private final String adminPassword;
    private final String playerEmail;
    private final String playerPassword;

    public SeedUsersRunner(SeedUsersService seedUsersService,
                           @Value("${SEED_ADMIN_EMAIL:admin.demo@padelpro.es}") String adminEmail,
                           @Value("${SEED_ADMIN_PASSWORD:PadelDemo#Admin2026}") String adminPassword,
                           @Value("${SEED_PLAYER_EMAIL:jugador.demo@padelpro.es}") String playerEmail,
                           @Value("${SEED_PLAYER_PASSWORD:PadelDemo#Jugador2026}") String playerPassword) {
        this.seedUsersService = seedUsersService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.playerEmail = playerEmail;
        this.playerPassword = playerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean admin = seedUsersService.seedIfAbsent(adminEmail, adminPassword, "Admin", "Prueba", UserRole.ADMIN);
        boolean player = seedUsersService.seedIfAbsent(playerEmail, playerPassword, "Jugador", "Prueba", UserRole.USER);
        log.info("Seed users: admin created={}, player created={} (SEED_USERS_ENABLED=true).", admin, player);
    }
}
