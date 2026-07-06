package com.padelpro.notificaciones.infrastructure.email;

import com.padelpro.notificaciones.domain.model.EmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD unit tests for the event email templates (task 2.2): each renders the expected facts and never
 * leaks sensitive data (RN-RGPD-04).
 */
@DisplayName("Event email templates — confirmation / cancellation / receipt content")
class EventEmailTemplatesTest {

    @Test
    @DisplayName("confirmation email carries date, time, duration and amount")
    void confirmation_content() {
        EmailMessage m = ReservationConfirmedEmailTemplate.build(
                "ana@example.com", "Ana", LocalDate.of(2030, 1, 10),
                LocalTime.of(18, 30), 90, new BigDecimal("22.50"));

        assertThat(m.recipient()).isEqualTo("ana@example.com");
        assertThat(m.subject()).contains("confirmada");
        assertThat(m.body())
                .contains("Ana")
                .contains("10/01/2030")
                .contains("18:30")
                .contains("90")
                .contains("22.50");
    }

    @Test
    @DisplayName("cancellation email includes the reason only when present")
    void cancellation_content() {
        EmailMessage withReason = ReservationCancelledEmailTemplate.build(
                "ana@example.com", "Ana", "Mantenimiento de la pista");
        assertThat(withReason.subject()).contains("cancelada");
        assertThat(withReason.body()).contains("Mantenimiento de la pista");

        EmailMessage noReason = ReservationCancelledEmailTemplate.build(
                "ana@example.com", "Ana", null);
        assertThat(noReason.body()).doesNotContain("Motivo:");
    }

    @Test
    @DisplayName("receipt email carries amount, date and reference — no card data (RN-RGPD-04)")
    void receipt_content() {
        EmailMessage m = PaymentReceiptEmailTemplate.build(
                "ana@example.com", "Ana", new BigDecimal("15.00"),
                OffsetDateTime.of(2030, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC), "AUTH-9988");

        assertThat(m.subject()).contains("Recibo");
        assertThat(m.body())
                .contains("15.00")
                .contains("10/01/2030")
                .contains("AUTH-9988");
        String lower = m.body().toLowerCase();
        assertThat(lower)
                .doesNotContain("cvv")
                .doesNotContain("tarjeta")
                .doesNotContain("card")
                .doesNotContain("token");
    }
}
