package com.padelpro.pagos.application.service;

import java.security.SecureRandom;

/**
 * Generates Redsys order ids (pagos-redsys-online, D5). Redsys requires the {@code Ds_Merchant_Order}
 * to start with 4 numeric digits followed by up to 8 alphanumeric characters (4–12 chars total).
 *
 * <p>Implemented as a class (not a lambda) so it can be stubbed deterministically in unit tests.
 */
public class RedsysOrderIdGenerator {

    private static final char[] ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    /** A fresh order id: 4 leading digits + 8 uppercase alphanumeric characters (12 chars). */
    public String generate() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 4; i++) {
            sb.append((char) ('0' + random.nextInt(10)));
        }
        for (int i = 0; i < 8; i++) {
            sb.append(ALNUM[random.nextInt(ALNUM.length)]);
        }
        return sb.toString();
    }
}
