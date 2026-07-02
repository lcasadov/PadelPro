package com.padelpro.auth.infrastructure.config;

import com.padelpro.auth.application.service.AdminSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the first administrator on application start (D1).
 *
 * <p>Reads {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} from the environment and delegates the
 * idempotent decision to {@link AdminSeedService}. Never logs the credentials (RN-RGPD-04);
 * only whether an admin was created.
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final AdminSeedService adminSeedService;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeedRunner(AdminSeedService adminSeedService,
                           @Value("${ADMIN_EMAIL:}") String adminEmail,
                           @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.adminSeedService = adminSeedService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = adminSeedService.seedIfAbsent(adminEmail, adminPassword);
        if (created) {
            log.info("Bootstrap admin created (email taken from ADMIN_EMAIL).");
        } else {
            log.debug("Bootstrap admin not created (already present or credentials absent).");
        }
    }
}
