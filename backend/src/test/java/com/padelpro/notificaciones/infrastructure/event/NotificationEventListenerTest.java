package com.padelpro.notificaciones.infrastructure.event;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.application.service.TelegramNotificationService;
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
 * Unit tests for {@link NotificationEventListener} (tasks 3.1/3.3/3.5 email + 4.1/4.2/4.5 Telegram):
 * each domain event resolves the owner, dispatches the matching email template, and — when the owner
 * has a linked Telegram chat (RN-TEL-03) — a direct Telegram message; a confirmed reservation also
 * triggers a group broadcast (Req 6). When the owner cannot be resolved (or is INACTIVE) nothing is
 * dispatched on any channel.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener — event → owner email + Telegram dispatch")
class NotificationEventListenerTest {

    private static final UUID RES_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final long OWNER_ID = 42L;
    private static final String CHAT_ID = "chat-42";

    @Mock private EmailNotificationService emailNotificationService;
    @Mock private TelegramNotificationService telegramNotificationService;
    @Mock private UserRepositoryPort userRepositoryPort;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(emailNotificationService, telegramNotificationService,
                userRepositoryPort);
    }

    private User owner() {
        return ownerWith(UserStatus.ACTIVE, null);
    }

    private User ownerLinked() {
        return ownerWith(UserStatus.ACTIVE, CHAT_ID);
    }

    private User ownerWith(UserStatus status, String telegramChatId) {
        User u = new User("ana", "$2a$hash", "Ana", "García", "ana@example.com",
                UserRole.USER, status, OffsetDateTime.now(), OffsetDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", OWNER_ID);
        u.setTelegramChatId(telegramChatId);
        return u;
    }

    // ----------------------------------------------------------------------- email (existing)

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
        assertThat(msg.body()).contains("15,00");
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
        verify(telegramNotificationService, never()).dispatchDirect(any(), any(), any(), any(), any());
        verify(telegramNotificationService, never()).dispatchGroup(any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → confirmation email is NOT dispatched")
    void inactive_owner_skips_confirmed_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWith(UserStatus.INACTIVE, CHAT_ID)));

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60,
                new BigDecimal("15.00")));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
        verify(telegramNotificationService, never()).dispatchDirect(any(), any(), any(), any(), any());
        verify(telegramNotificationService, never()).dispatchGroup(any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → cancellation email is NOT dispatched")
    void inactive_owner_skips_cancelled_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWith(UserStatus.INACTIVE, CHAT_ID)));

        listener.onReservationCancelled(new ReservationCancelledEmailEvent(
                RES_ID, OWNER_ID, "Lluvia"));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
        verify(telegramNotificationService, never()).dispatchDirect(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RGPD: INACTIVE (soft-deleted) owner → payment receipt is NOT dispatched")
    void inactive_owner_skips_paid_dispatch() {
        when(userRepositoryPort.findById(OWNER_ID))
                .thenReturn(Optional.of(ownerWith(UserStatus.INACTIVE, CHAT_ID)));

        listener.onPaymentPaid(new PaymentPaidEmailEvent(
                RES_ID, OWNER_ID, new BigDecimal("15.00"), OffsetDateTime.now(), "AUTH-777"));

        verify(emailNotificationService, never()).dispatch(any(), any(), any(), any());
        verify(telegramNotificationService, never()).dispatchDirect(any(), any(), any(), any(), any());
    }

    // ----------------------------------------------------------------------- Telegram (new)

    @Test
    @DisplayName("4.1 confirmed + linked chat → email AND direct Telegram AND group broadcast")
    void confirmed_linked_dispatches_email_and_telegram() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(ownerLinked()));

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60,
                new BigDecimal("15.00")));

        verify(emailNotificationService).dispatch(any(), eq(OWNER_ID), eq("RESERVATION"),
                eq(RES_ID.toString()));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).dispatchDirect(eq(CHAT_ID), text.capture(),
                eq(OWNER_ID), eq("RESERVATION"), eq(RES_ID.toString()));
        assertThat(text.getValue()).contains("confirmada").contains("15,00");

        verify(telegramNotificationService).dispatchGroup(any(), eq("RESERVATION"),
                eq(RES_ID.toString()));
    }

    @Test
    @DisplayName("4.2 confirmed WITHOUT linked chat → email only, no direct Telegram")
    void confirmed_unlinked_dispatches_email_only() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(owner()));

        listener.onReservationConfirmed(new ReservationConfirmedEmailEvent(
                RES_ID, OWNER_ID, LocalDate.of(2030, 1, 10), LocalTime.of(18, 0), 60,
                new BigDecimal("15.00")));

        verify(emailNotificationService).dispatch(any(), eq(OWNER_ID), eq("RESERVATION"),
                eq(RES_ID.toString()));
        verify(telegramNotificationService, never()).dispatchDirect(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("4.5 cancelled + linked chat → direct Telegram with the reason")
    void cancelled_linked_dispatches_telegram() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(ownerLinked()));

        listener.onReservationCancelled(new ReservationCancelledEmailEvent(
                RES_ID, OWNER_ID, "Lluvia"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).dispatchDirect(eq(CHAT_ID), text.capture(),
                eq(OWNER_ID), eq("RESERVATION"), eq(RES_ID.toString()));
        assertThat(text.getValue()).contains("cancelada").contains("Lluvia");
    }

    @Test
    @DisplayName("4.5 payment paid + linked chat → direct Telegram receipt with amount/reference")
    void paid_linked_dispatches_telegram_receipt() {
        when(userRepositoryPort.findById(OWNER_ID)).thenReturn(Optional.of(ownerLinked()));

        listener.onPaymentPaid(new PaymentPaidEmailEvent(
                RES_ID, OWNER_ID, new BigDecimal("15.00"), OffsetDateTime.now(), "AUTH-777"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramNotificationService).dispatchDirect(eq(CHAT_ID), text.capture(),
                eq(OWNER_ID), eq("PAYMENT"), eq(RES_ID.toString()));
        assertThat(text.getValue()).contains("15,00").contains("AUTH-777");
    }
}
