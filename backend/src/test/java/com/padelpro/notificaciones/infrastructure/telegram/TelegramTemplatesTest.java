package com.padelpro.notificaciones.infrastructure.telegram;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the plain-text Telegram templates (task 2.1): the messages carry the reservation /
 * payment facts, use the Spanish decimal comma, and never leak personal data into the group broadcast.
 */
@DisplayName("Telegram templates — plain-text bodies with reservation/payment facts")
class TelegramTemplatesTest {

    private static final LocalDate DATE = LocalDate.of(2030, 1, 10);
    private static final LocalTime TIME = LocalTime.of(18, 30);

    @Test
    @DisplayName("confirmed direct → greeting, slot facts, Spanish-formatted amount")
    void confirmed_direct() {
        String body = ReservationConfirmedTelegramTemplate.direct(
                "Ana", DATE, TIME, 90, new BigDecimal("22.50"));

        assertThat(body)
                .contains("Ana")
                .contains("confirmada")
                .contains("10/01/2030")
                .contains("18:30")
                .contains("90 minutos")
                .contains("22,50");
    }

    @Test
    @DisplayName("confirmed direct without name → neutral greeting")
    void confirmed_direct_no_name() {
        String body = ReservationConfirmedTelegramTemplate.direct(null, DATE, TIME, 60, null);

        assertThat(body).startsWith("Hola,").contains("10/01/2030");
    }

    @Test
    @DisplayName("confirmed group → slot facts only, no personal name")
    void confirmed_group_has_no_personal_data() {
        String body = ReservationConfirmedTelegramTemplate.group(DATE, TIME, 60);

        assertThat(body)
                .contains("10/01/2030")
                .contains("18:30")
                .contains("60 minutos")
                .doesNotContain("Ana");
    }

    @Test
    @DisplayName("cancelled direct → includes the reason when present")
    void cancelled_direct_with_reason() {
        String body = ReservationCancelledTelegramTemplate.direct("Ana", "Lluvia");

        assertThat(body).contains("cancelada").contains("Lluvia");
    }

    @Test
    @DisplayName("cancelled direct → omits the reason line when absent")
    void cancelled_direct_without_reason() {
        String body = ReservationCancelledTelegramTemplate.direct("Ana", null);

        assertThat(body).contains("cancelada").doesNotContain("Motivo:");
    }

    @Test
    @DisplayName("payment receipt direct → amount, date and reference")
    void payment_receipt_direct() {
        OffsetDateTime paidAt = OffsetDateTime.of(2030, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);

        String body = PaymentReceiptTelegramTemplate.direct(
                "Ana", new BigDecimal("15.00"), paidAt, "AUTH-777");

        assertThat(body)
                .contains("pago")
                .contains("15,00")
                .contains("10/01/2030")
                .contains("AUTH-777");
    }
}
