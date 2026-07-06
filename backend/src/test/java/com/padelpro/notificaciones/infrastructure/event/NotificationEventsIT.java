package com.padelpro.notificaciones.infrastructure.event;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.model.NotificationStatus;
import com.padelpro.notificaciones.infrastructure.persistence.NotificationLogJpaRepository;
import com.padelpro.reservas.application.service.AdminReservaService;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationChannel;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import com.padelpro.shared.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Integration tests for the event-driven notification wiring (tasks 3.1/3.3/3.5) on a real
 * PostgreSQL: publishing happens inside the reservation/payment transaction and delivery runs in an
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} pipeline, so it survives a broken
 * SMTP without reverting the business change (RN-NOT-01/02).
 *
 * <p>{@link JavaMailSender} is mocked so no real SMTP is needed; the send can be made to succeed or
 * throw to exercise both the SENT and FAILED branches.
 */
@DisplayName("Notificaciones — eventos de dominio + AFTER_COMMIT (Postgres)")
class NotificationEventsIT extends PostgresIntegrationTest {

    @MockBean
    private JavaMailSender mailSender;

    @Autowired private AdminReservaService adminReservaService;
    @Autowired private UserRepositoryPort userRepositoryPort;
    @Autowired private ReservationCommandPort reservationCommandPort;
    @Autowired private PaymentCommandPort paymentCommandPort;
    @Autowired private NotificationLogJpaRepository notificationLogRepository;

    private User seedOwner() {
        String unique = String.valueOf(System.nanoTime());
        User u = new User("owner" + unique.substring(unique.length() - 6), "$2a$12$abcdefghijklmnop",
                "Ana", "García", "ana" + unique + "@example.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        return userRepositoryPort.save(u);
    }

    private Reservation seedPendingReservation(Long ownerId) {
        Reservation r = Reservation.create(ownerId, LocalDate.now().plusDays(5),
                LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);
        Reservation saved = reservationCommandPort.save(r);
        paymentCommandPort.save(Payment.pendingFor(saved.getId(), new BigDecimal("15.00")));
        return saved;
    }

    /** Poll notification_log for an entry for the given related entity, up to ~8s (async delivery). */
    private Optional<NotificationLog> awaitNotification(String relatedId) throws InterruptedException {
        for (int i = 0; i < 80; i++) {
            List<NotificationLog> all = notificationLogRepository.findAll();
            Optional<NotificationLog> match = all.stream()
                    .filter(n -> relatedId.equals(n.getRelatedEntityId()))
                    .findFirst();
            if (match.isPresent() && match.get().getStatus() != NotificationStatus.PENDING) {
                return match;
            }
            Thread.sleep(100);
        }
        return Optional.empty();
    }

    @Test
    @DisplayName("3.1 confirmar reserva → email SENT registrado y reserva sigue CONFIRMED")
    void confirm_sends_email_and_keeps_confirmed() throws InterruptedException {
        // mailSender.send succeeds (default mock behaviour → no exception)
        User owner = seedOwner();
        Reservation reservation = seedPendingReservation(owner.getId());

        adminReservaService.cambiarEstado(reservation.getId(), "CONFIRMED");

        // Business change committed and not reverted.
        assertThat(reservationCommandPort.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);

        NotificationLog entry = awaitNotification(reservation.getId().toString())
                .orElseThrow(() -> new AssertionError("no notification recorded for confirmation"));
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entry.getRecipient()).isEqualTo(owner.getEmail());
        assertThat(entry.getRelatedEntityType()).isEqualTo("RESERVATION");
    }

    @Test
    @DisplayName("3.3 SMTP caído al cancelar → reserva CANCELLED (no revierte) + registro FAILED")
    void cancel_with_smtp_down_does_not_revert_business() throws InterruptedException {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        User owner = seedOwner();
        Reservation reservation = seedPendingReservation(owner.getId());

        adminReservaService.cambiarEstado(reservation.getId(), "CANCELLED");

        // RN-NOT-01/02: the SMTP failure must NOT revert the cancellation.
        assertThat(reservationCommandPort.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELLED);

        NotificationLog entry = awaitNotification(reservation.getId().toString())
                .orElseThrow(() -> new AssertionError("no notification recorded for cancellation"));
        assertThat(entry.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entry.getErrorMessage()).isNotBlank();
        // RN-RGPD-04: the stored error is a sanitized summary, never the email body.
        assertThat(entry.getErrorMessage()).doesNotContain("Ana").doesNotContain("@example.com");
    }
}
