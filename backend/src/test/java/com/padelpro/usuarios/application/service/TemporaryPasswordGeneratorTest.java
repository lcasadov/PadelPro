package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.PasswordPolicy;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TemporaryPasswordGenerator} (group 5.5) — an isolated generator that
 * produces random passwords which always satisfy the {@link PasswordPolicy}.
 */
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generated_password_satisfies_policy() {
        for (int i = 0; i < 100; i++) {
            String pwd = generator.generate();
            // Must not throw
            PasswordPolicy.validate(pwd);
            assertThat(pwd).hasSizeGreaterThanOrEqualTo(8);
        }
    }

    @Test
    void generated_passwords_are_random_and_unique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(generator.generate());
        }
        // Overwhelmingly likely all-distinct; allow a tiny margin against flakiness.
        assertThat(seen).hasSizeGreaterThan(95);
    }
}
