package com.padelpro.usuarios.application.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates cryptographically-random temporary passwords for admin resets (D3/D4).
 *
 * <p>Every generated value satisfies the password policy (length ≥ 8, at least one uppercase and
 * one digit) by construction. Symbols that are ambiguous when read aloud/copied (0/O, 1/l/I) are
 * excluded so the admin can reliably communicate the value to the user.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";  // no I, O
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";  // no l, o
    private static final String DIGITS = "23456789";                 // no 0, 1
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    /**
     * @return a new 12-character temporary password guaranteed to meet the policy.
     */
    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        // Guarantee at least one uppercase and one digit.
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        for (int i = sb.length(); i < LENGTH; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }
        // Shuffle so the guaranteed chars are not always in fixed positions.
        return shuffle(sb.toString());
    }

    private String shuffle(String s) {
        char[] chars = s.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
