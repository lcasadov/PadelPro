package com.padelpro.reservas.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Backend-only price calculation (RN-RES-03, D3).
 *
 * <pre>amount = price_per_hour × duration_minutes / 60</pre>
 *
 * <p>The result is the frozen {@code payments.amount}; any amount sent by the client is ignored.
 * Rounding is HALF_UP to 2 decimals to match the {@code NUMERIC(12,2)} column.
 */
public final class PriceCalculator {

    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    private PriceCalculator() {
    }

    public static BigDecimal calculate(BigDecimal pricePerHour, int durationMinutes) {
        if (pricePerHour == null) {
            throw new IllegalArgumentException("pricePerHour must not be null");
        }
        return pricePerHour
                .multiply(BigDecimal.valueOf(durationMinutes))
                .divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }
}
