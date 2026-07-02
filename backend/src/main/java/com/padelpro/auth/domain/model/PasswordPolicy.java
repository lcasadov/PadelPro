package com.padelpro.auth.domain.model;

import com.padelpro.auth.domain.exception.InvalidPasswordException;

import java.util.ArrayList;
import java.util.List;

/**
 * Password policy (RN-AUTH-08): 8–128 characters, at least one uppercase letter and one digit.
 *
 * <p>Pure domain object with no dependencies, reused by registration and by the user's own
 * password change (D9) so the rule is defined in exactly one place.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /**
     * Validate a raw password against the policy.
     *
     * @throws InvalidPasswordException listing every violated rule code
     * @throws IllegalArgumentException if the password is {@code null}
     */
    public static void validate(String password) {
        if (password == null) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELD: password");
        }
        List<String> violations = new ArrayList<>();
        if (password.length() < 8)                              violations.add("MIN_LENGTH_8");
        if (password.length() > 128)                            violations.add("MAX_LENGTH_128");
        if (password.chars().noneMatch(Character::isUpperCase)) violations.add("REQUIRES_UPPERCASE");
        if (password.chars().noneMatch(Character::isDigit))     violations.add("REQUIRES_NUMBER");
        if (!violations.isEmpty()) {
            throw new InvalidPasswordException(violations);
        }
    }
}
