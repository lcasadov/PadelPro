package com.padelpro.reservas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PriceCalculator} (task 8.1, RN-RES-03, D3).
 *
 * <p>Verifies the backend-only formula {@code amount = price_per_hour × duration_minutes / 60},
 * HALF_UP rounding to 2 decimals (to match {@code NUMERIC(12,2)}), and null handling.
 */
@DisplayName("Unit — PriceCalculator")
class PriceCalculatorTest {

    // 8.1 — formula across the allowed durations with a round hourly price
    @ParameterizedTest(name = "{0}€/h × {1}min → {2}€")
    @CsvSource({
            "15.00, 60, 15.00",
            "15.00, 90, 22.50",
            "15.00, 120, 30.00",
            "15.00, 150, 37.50",
            "15.00, 180, 45.00",
            "20.00, 90, 30.00",
            "12.00, 90, 18.00"
    })
    @DisplayName("calcula importe = precio/hora × minutos / 60")
    void should_calculate_amount_when_valid_inputs(String pricePerHour, int minutes, String expected) {
        BigDecimal result = PriceCalculator.calculate(new BigDecimal(pricePerHour), minutes);

        assertThat(result).isEqualByComparingTo(expected);
    }

    // 8.1 — HALF_UP rounding to 2 decimals (matches NUMERIC(12,2))
    @Test
    @DisplayName("redondea HALF_UP a 2 decimales")
    void should_round_half_up_to_two_decimals() {
        // 13.33 €/h × 90 min / 60 = 19.995 → 20.00 (HALF_UP)
        BigDecimal result = PriceCalculator.calculate(new BigDecimal("13.33"), 90);

        assertThat(result).isEqualByComparingTo("20.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    // 8.1 — result always has scale 2 even for an exact integer total
    @Test
    @DisplayName("siempre escala 2 incluso con total entero")
    void should_have_scale_two_even_for_integer_total() {
        BigDecimal result = PriceCalculator.calculate(new BigDecimal("10"), 60);

        assertThat(result.scale()).isEqualTo(2);
        assertThat(result).isEqualByComparingTo("10.00");
    }

    // 8.1 — null price is a programming error → IllegalArgumentException
    @Test
    @DisplayName("lanza IllegalArgumentException si pricePerHour es null")
    void should_throw_when_price_per_hour_null() {
        assertThatThrownBy(() -> PriceCalculator.calculate(null, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pricePerHour");
    }
}
