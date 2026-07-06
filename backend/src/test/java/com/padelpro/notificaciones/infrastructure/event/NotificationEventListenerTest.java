package com.padelpro.notificaciones.infrastructure.event;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationCancelledEmailEvent;
import com.padelpro.notificaciones.domain.event.ReservationConfirmedEmailEvent;
import com.padelpro.notificaciones.domain.model.EmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationEventListener} (tasks 3.1/3.3/3.5): each domain event resolves the
 * owner's email and dispatches the matching template; when the owner cannot be resolved (or has no
 * email) nothing is dispatched.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener — event → owner email dispatch")
class NotificationEventListenerTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long OWNER_ID = 42L;

    @Mock private EmailNotificationService emailNotificationService;
    @Mock private UserRepositoryPort userRepositoryPort;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(emailNotificationService, userRepositoryPort);
    }

    private User owner() {
        return ownerWithStatus(UserStatus.ACTIVE);
    }

    private User ownerWithStatus(UserStatus status) {
        User u = new User("ana", "$2a$hash", "Ana", "García", "ana@example.com",
                UserRole.USER, status, OffsetDateTime.now(), OffsetDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", OWNER_ID);
        return u;
    }

    @Test
    @DisplayName("3.1 reservation confirmed → confirmation email to the owner")
    void confirmed_dispatches_to_owner() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(owner()));

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60,
                new BigDecimal("15.00")));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailNotificationService).dispatch(captor.capture(), eq(OWNER_ID),
                eq("RESERVATION"), eq(RES_ID.toString()));
        EmailMessage msg = captor.getValue();
        assertThat(msg.recipient()).isEqualTo("ana@example.com");
        assertThat(msg.subject()).contains("confirmada");
        assertThat(msg.body()).contains("15.00");
    }

    @Test
    @DisplayName("3.3 reservation cancelled → cancellation email with reason to the owner")
    void cancelled_dispatches_with_reason() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(owner()));

        listener.onReservationCancelled(new ReservationCancelledEmailEvent(
                RES_ID, OWNER_ID, "Lluvia"));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailNotificationService).dispatch(captor.capture(), eq(OWNER_ID),
                eq("RESERVATION"), eq(RES_ID.toString()));
        assertThat(captor.getValue().subject()).contains("cancelada");
        assertThat(captor.getValue().body()).contains("Lluvia");
    }

    @Test
    @DisplayName("3.5 payment paid → receipt email with amount/reference to the owner")
    void paid_dispatches_receipt() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(owner()));

        listener.onPaymentPaid(new PaymentPaidEmailEvent(
                RES_ID, OWNER_ID, new BigDecimal("15.00"), OffsetDateTime.now(), "AUTH-777"));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailNotificationService).dispatch(captor.capture(), eq(OWNER_ID),
                eq("PAYMENT"), eq(RES_ID.toString()));
        assertThat(captor.getValue().subject()).contains("Recibo");
        assertThat(captor.getValue().body()).contains("AUTH-777");
    }

    @Test
    @DisplayName("owner not resolvable → no email dispatched")
    void unknown_owner_skips_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.empty());

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60, null));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → confirmation email is NOT dispatched")
    void inactive_owner_skips_confirmed_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithStatus(UserStatus.INACTIVE)));

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60,
                new BigDecimal("15.00")));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → cancellation email is NOT dispatched")
    void inactive_owner_skips_cancelled_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithStatus(UserStatus.INACTIVE)));

        listener.onReservationCancelled(new ReservationCancelledEmailEvent(
                RES_ID, OWNER_ID, "Lluvia"));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → payment receipt is NOT dispatched")
    void inactive_owner_skips_paid_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWithStatus(UserStatus.INACTIVE)));

        listener.onPaymentPaid(new PaymentPaidEmailEvent(
                RES_ID, OWNER_ID, new BigDecimal("15.00"), OffsetDateTime.now(), "AUTH-777"));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
    }
}
