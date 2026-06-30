package com.padelpro.auth.infrastructure.config;

import com.padelpro.auth.application.service.AdminSeedService;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.infrastructure.persistence.UserRepository;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the admin-seed bootstrap (D1) wired through Spring against a real
 * PostgreSQL. Verifies the seed creates an ACTIVE ADMIN with a BCrypt hash, is idempotent,
 * and that absent credentials create nothing.
 *
 * <p>The {@code @Sql cleanup.sql} (inherited) deletes all users {@code @BeforeEach}, so each
 * method starts from an empty {@code users} table regardless of the runner having executed at
 * context startup. The {@link AdminSeedService} is re-invoked explicitly inside each test to
 * exercise the seed decision deterministically.
 */
@TestPropertySource(properties = {
        "ADMIN_EMAIL=boss@club.local",
        "ADMIN_PASSWORD=S3cretBootstrap1"
})
class AdminSeedRunnerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AdminSeedService adminSeedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    @DisplayName("2.1 seeds an ACTIVE ADMIN with a BCrypt hash when none exists")
    void seeds_active_admin_with_bcrypt_when_none_exists() {
        boolean created = adminSeedService.seedIfAbsent("boss@club.local", "S3cretBootstrap1");

        assertThat(created).isTrue();
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .toList();
        assertThat(admins).hasSize(1);
        User admin = admins.get(0);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getEmail()).isEqualTo("boss@club.local");
        assertThat(passwordEncoder.matches("S3cretBootstrap1", admin.getPasswordHash())).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo("S3cretBootstrap1");
    }

    @Test
    @DisplayName("2.3 is idempotent — a second run creates no additional admin")
    void is_idempotent_when_admin_already_exists() {
        assertThat(adminSeedService.seedIfAbsent("boss@club.local", "S3cretBootstrap1")).isTrue();
        boolean secondRun = adminSeedService.seedIfAbsent("other@club.local", "Another1Pass");

        assertThat(secondRun).isFalse();
        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .count();
        assertThat(adminCount).isEqualTo(1);
    }

    @Test
    @DisplayName("2.4 creates nothing when credentials are absent (no failure)")
    void creates_nothing_when_credentials_absent() {
        assertThat(adminSeedService.seedIfAbsent("", "")).isFalse();
        assertThat(adminSeedService.seedIfAbsent(null, null)).isFalse();
        assertThat(userRepository.findAll()).isEmpty();
    }
}
