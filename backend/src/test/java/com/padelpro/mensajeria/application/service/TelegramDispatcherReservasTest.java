package com.padelpro.mensajeria.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.model.UserRole;
import com.padelpro.auth.domain.model.UserStatus;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.mensajeria.domain.audit.TelegramAuditActions;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.otp.application.dto.GeneratedOtp;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.exception.OtpVerificationException;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.application.service.CancelarReservaService;
import com.padelpro.reservas.application.service.ConfirmarReservaService;
import com.padelpro.reservas.application.service.CrearReservaService;
import com.padelpro.reservas.application.service.ReservaQueryService;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.SlotConflictException;
import com.padelpro.reservas.domain.model.ReservationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Telegram reservation command dispatcher (capability bot-telegram-reservas):
 * {@code /ayuda}, {@code /misreservas}, {@code /reservar}, {@code /cancelar}, {@code /confirmar},
 * account-linking guard, strict parsing, reservation-bound OTP flows (D-4), webhook-retry idempotency,
 * prefix-collision handling (D-2) and auditing (RN-RGPD-04: no OTP in clear).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelegramWebhookService — reservation command dispatcher (bot-telegram-reservas)")
class TelegramDispatcherReservasTest {

    private static final String CHAT = "555";
    private static final Long USER_ID = 42L;
    private static final UUID RES_ID = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");
    private static final String REF = "a1b2c3d4"; // first 8 hex of RES_ID

    @Mock private TelegramConfigService configService;
    @Mock private OtpService otpService;
    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private TelegramAuditRecorder auditRecorder;
    @Mock private TelegramPort telegramPort;
    @Mock private CrearReservaService crearReservaService;
    @Mock private ConfirmarReservaService confirmarReservaService;
    @Mock private CancelarReservaService cancelarReservaService;
    @Mock private ReservaQueryService reservaQueryService;

    private TelegramWebhookService service;

    @BeforeEach
    void setUp() {
        service = new TelegramWebhookService(configService, otpService, userRepositoryPort,
                auditRecorder, telegramPort, new ObjectMapper(), "http://host/perfil",
                crearReservaService, confirmarReservaService, cancelarReservaService, reservaQueryService);
        lenient().when(configService.getWebhookSecret()).thenReturn(Optional.of("s3cret"));
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static User linkedUser() {
        User user = new User("juan", "hash", "Juan", "Pérez", "juan@x.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        user.setTelegramChatId(CHAT);
        setId(user, USER_ID);
        return user;
    }

    private static User userWithId(long id, String login) {
        User user = new User(login, "hash", "N", "L", login + "@x.com",
                UserRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(), OffsetDateTime.now());
        setId(user, id);
        return user;
    }

    private static void setId(User user, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ReservaResponse reserva(UUID id, Long ownerId, String status) {
        return new ReservaResponse(id.toString(), ownerId,
                LocalDate.of(2099, 8, 1), LocalTime.of(18, 0), LocalTime.of(19, 30), 90,
                status, "TELEGRAM", null, null, List.of(), null, OffsetDateTime.now());
    }

    private static String update(String text) {
        return "{\"update_id\":7001,\"message\":{\"chat\":{\"id\":" + CHAT + "},\"text\":\"" + text + "\"}}";
    }

    private void linked() {
        when(userRepositoryPort.findByTelegramChatId(CHAT)).thenReturn(Optional.of(linkedUser()));
    }

    private void unlinked() {
        when(userRepositoryPort.findByTelegramChatId(CHAT)).thenReturn(Optional.empty());
    }

    // ── /ayuda + unknown ──────────────────────────────────────────────────────

    @Test
    @DisplayName("/ayuda lists the available commands")
    void ayuda_lists_commands() {
        service.handleUpdate("s3cret", update("/ayuda"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(telegramPort).enviarMensaje(eq(CHAT), msg.capture());
        assertThat(msg.getValue())
                .contains("/reservar").contains("/misreservas")
                .contains("/confirmar").contains("/cancelar");
    }

    @Test
    @DisplayName("unknown command from a linked chat → help text")
    void unknown_command_linked_returns_help() {
        linked();

        service.handleUpdate("s3cret", update("/foobar"));

        verify(telegramPort).enviarMensaje(eq(CHAT), contains("/reservar"));
    }

    @Test
    @DisplayName("plain message from a linked chat → no reply")
    void plain_message_linked_no_reply() {
        linked();

        service.handleUpdate("s3cret", update("hola"));

        verifyNoInteractions(telegramPort);
    }

    // ── linking guard ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("business command from an unlinked chat → 'link first', no business op")
    void business_command_unlinked_rejected() {
        unlinked();

        service.handleUpdate("s3cret", update("/misreservas"));

        verify(telegramPort).enviarMensaje(eq(CHAT), contains("Vincula primero"));
        verifyNoInteractions(reservaQueryService);
    }

    // ── /misreservas ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("/misreservas")
    class MisReservas {

        @Test
        @DisplayName("lists the user's reservations with a short reference")
        void lists_with_short_ref() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION")));

            service.handleUpdate("s3cret", update("/misreservas"));

            ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
            verify(telegramPort).enviarMensaje(eq(CHAT), msg.capture());
            assertThat(msg.getValue()).contains(REF).contains("pendiente");
        }

        @Test
        @DisplayName("no reservations → suggests /reservar")
        void empty_suggests_reservar() {
            linked();
            when(reservaQueryService.listForUser(USER_ID)).thenReturn(List.of());

            service.handleUpdate("s3cret", update("/misreservas"));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("/reservar"));
        }
    }

    // ── /reservar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("/reservar")
    class Reservar {

        @Test
        @DisplayName("valid command creates the reservation (TELEGRAM channel), emits a reservation-bound OTP and audits")
        void creates_and_emits_otp() {
            linked();
            when(crearReservaService.crear(eq(USER_ID), any(CrearReservaRequest.class), eq("tg-7001"),
                    eq(ReservationChannel.TELEGRAM)))
                    .thenReturn(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION"));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID)).thenReturn(Optional.empty());
            when(otpService.generate(USER_ID, OtpType.RESERVATION_CONFIRM, RES_ID))
                    .thenReturn(new GeneratedOtp("246810", OffsetDateTime.now().plusMinutes(10)));

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00 90"));

            verify(otpService).generate(USER_ID, OtpType.RESERVATION_CONFIRM, RES_ID);
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_RESERVA_CREATED), eq(USER_ID), any());
            verify(telegramPort).enviarMensaje(eq(CHAT), contains(REF));
        }

        @Test
        @DisplayName("webhook retry (reservation already has an active OTP) does not re-emit or re-audit")
        void retry_is_idempotent() {
            linked();
            // crear short-circuits on the idempotency key and returns the same reservation.
            when(crearReservaService.crear(eq(USER_ID), any(), eq("tg-7001"), eq(ReservationChannel.TELEGRAM)))
                    .thenReturn(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION"));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.RESERVATION_CONFIRM));

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00 90"));

            verify(otpService, never()).generate(anyLong(), any(), any());
            verify(auditRecorder, never()).record(eq(TelegramAuditActions.TELEGRAM_RESERVA_CREATED), any(), any());
            verify(telegramPort).enviarMensaje(eq(CHAT), contains("ya estaba registrada"));
        }

        @Test
        @DisplayName("default duration is applied when omitted")
        void default_duration_applied() {
            linked();
            ArgumentCaptor<CrearReservaRequest> req = ArgumentCaptor.forClass(CrearReservaRequest.class);
            when(crearReservaService.crear(eq(USER_ID), req.capture(), any(), eq(ReservationChannel.TELEGRAM)))
                    .thenReturn(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION"));
            when(otpService.peekActiveReservationType(anyLong(), any())).thenReturn(Optional.empty());
            when(otpService.generate(anyLong(), any(), any()))
                    .thenReturn(new GeneratedOtp("111111", OffsetDateTime.now().plusMinutes(10)));

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00"));

            assertThat(req.getValue().durationMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("invalid format → help with example, nothing created, audits rejection")
        void invalid_format_returns_help() {
            linked();

            service.handleUpdate("s3cret", update("/reservar mañana por la tarde"));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("Ejemplo"));
            verify(crearReservaService, never()).crear(any(), any(), any(), any());
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }

        @Test
        @DisplayName("slot conflict from the service is relayed legibly and audited")
        void slot_conflict_relayed() {
            linked();
            when(crearReservaService.crear(any(), any(), any(), any()))
                    .thenThrow(new SlotConflictException("El tramo ya está ocupado"));

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00 90"));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("No se pudo crear"));
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
            verify(otpService, never()).generate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("@handle resolves to a registered participant; a free token is an external guest")
        void participants_resolved() {
            linked();
            when(userRepositoryPort.findByLogin("ana")).thenReturn(Optional.of(userWithId(7L, "ana")));
            ArgumentCaptor<CrearReservaRequest> req = ArgumentCaptor.forClass(CrearReservaRequest.class);
            when(crearReservaService.crear(eq(USER_ID), req.capture(), any(), eq(ReservationChannel.TELEGRAM)))
                    .thenReturn(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION"));
            when(otpService.peekActiveReservationType(anyLong(), any())).thenReturn(Optional.empty());
            when(otpService.generate(anyLong(), any(), any()))
                    .thenReturn(new GeneratedOtp("222222", OffsetDateTime.now().plusMinutes(10)));

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00 90 @ana Invitado"));

            List<CrearReservaRequest.ParticipanteAdicional> ps = req.getValue().participantesAdicionales();
            assertThat(ps).hasSize(2);
            assertThat(ps.get(0).userId()).isEqualTo(7L);
            assertThat(ps.get(1).externalName()).isEqualTo("Invitado");
        }

        @Test
        @DisplayName("an unresolved @handle rejects the whole command, nothing created")
        void unresolved_handle_rejected() {
            linked();
            when(userRepositoryPort.findByLogin("ghost")).thenReturn(Optional.empty());

            service.handleUpdate("s3cret", update("/reservar 2026-08-01 18:00 90 @ghost"));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("@ghost"));
            verify(crearReservaService, never()).crear(any(), any(), any(), any());
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }
    }

    // ── /cancelar (step 1) ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("/cancelar")
    class Cancelar {

        @Test
        @DisplayName("an owned reservation emits a reservation-bound CANCELLATION_CONFIRM OTP")
        void owned_emits_cancellation_otp() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "CONFIRMED")));
            when(otpService.generate(USER_ID, OtpType.CANCELLATION_CONFIRM, RES_ID))
                    .thenReturn(new GeneratedOtp("333333", OffsetDateTime.now().plusMinutes(10)));

            service.handleUpdate("s3cret", update("/cancelar " + REF));

            verify(otpService).generate(USER_ID, OtpType.CANCELLATION_CONFIRM, RES_ID);
            verify(telegramPort).enviarMensaje(eq(CHAT), contains("/confirmar " + REF));
            verify(cancelarReservaService, never()).cancelar(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("a reservation owned by someone else is treated as not found (BOLA / RN-AUTH-02)")
        void foreign_reservation_not_found() {
            linked();
            when(reservaQueryService.listForUser(USER_ID)).thenReturn(List.of());

            service.handleUpdate("s3cret", update("/cancelar " + REF));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("No encuentro esa reserva"));
            verify(otpService, never()).generate(anyLong(), any(), any());
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }

        @Test
        @DisplayName("an ambiguous prefix asks for the full identifier (D-2), emits no OTP")
        void ambiguous_prefix_asks_full_id() {
            linked();
            UUID other = UUID.fromString("a1b2ffff-0000-4000-8000-000000000002");
            when(reservaQueryService.listForUser(USER_ID)).thenReturn(List.of(
                    reserva(RES_ID, USER_ID, "CONFIRMED"),
                    reserva(other, USER_ID, "CONFIRMED")));

            service.handleUpdate("s3cret", update("/cancelar a1b2")); // prefix matches both

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("identificador completo"));
            verify(otpService, never()).generate(anyLong(), any(), any());
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }
    }

    // ── /confirmar (step 2) ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("/confirmar")
    class Confirmar {

        @Test
        @DisplayName("a reservation with a pending RESERVATION_CONFIRM → confirms and audits")
        void confirms_reservation() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.RESERVATION_CONFIRM));

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 246810"));

            verify(otpService).verifyForReservation(USER_ID, RES_ID, "246810");
            verify(confirmarReservaService).confirmar(RES_ID, USER_ID);
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_RESERVA_CONFIRMED), eq(USER_ID), any());
            verify(telegramPort).enviarMensaje(eq(CHAT), contains("confirmada"));
        }

        @Test
        @DisplayName("a reservation with a pending CANCELLATION_CONFIRM → cancels and audits")
        void cancels_reservation() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "CONFIRMED")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.CANCELLATION_CONFIRM));

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 333333"));

            verify(otpService).verifyForReservation(USER_ID, RES_ID, "333333");
            verify(cancelarReservaService).cancelar(RES_ID, USER_ID, false);
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_RESERVA_CANCELLED), eq(USER_ID), any());
            verify(telegramPort).enviarMensaje(eq(CHAT), contains("cancelada"));
        }

        @Test
        @DisplayName("cross case: confirming X never cancels X even with a cancellation pending on Y")
        void confirming_X_never_cancels_X_with_pending_cancel_on_Y() {
            linked();
            UUID y = UUID.fromString("bbbbcccc-0000-4000-8000-000000000009");
            // The user owns X (pending confirm) and Y (pending cancel). /confirmar X must confirm X.
            when(reservaQueryService.listForUser(USER_ID)).thenReturn(List.of(
                    reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION"),
                    reserva(y, USER_ID, "CONFIRMED")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.RESERVATION_CONFIRM));

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 246810"));

            verify(otpService).verifyForReservation(USER_ID, RES_ID, "246810");
            verify(confirmarReservaService).confirmar(RES_ID, USER_ID);
            verify(cancelarReservaService, never()).cancelar(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("a wrong OTP is relayed, audits rejection and never touches the reservation")
        void wrong_otp_rejected() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.RESERVATION_CONFIRM));
            org.mockito.Mockito.doThrow(OtpVerificationException.invalid())
                    .when(otpService).verifyForReservation(USER_ID, RES_ID, "000000");

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 000000"));

            verify(confirmarReservaService, never()).confirmar(any(), any());
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }

        @Test
        @DisplayName("no pending operation for the reservation → informs the user, nothing verified")
        void no_pending_operation() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID)).thenReturn(Optional.empty());

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 123456"));

            verify(otpService, never()).verifyForReservation(any(), any(), any());
            verify(telegramPort).enviarMensaje(eq(CHAT), contains("operación pendiente"));
        }

        @Test
        @DisplayName("business rejection (e.g. cancellation deadline) is relayed and audited")
        void business_rejection_relayed() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "CONFIRMED")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.CANCELLATION_CONFIRM));
            org.mockito.Mockito.doThrow(new InvalidReservaStateException(
                            "CANCELLATION_DEADLINE_PASSED", "La cancelación está fuera del plazo permitido"))
                    .when(cancelarReservaService).cancelar(RES_ID, USER_ID, false);

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 333333"));

            verify(telegramPort).enviarMensaje(eq(CHAT), contains("No se pudo completar"));
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), any());
        }

        @Test
        @DisplayName("RN-RGPD-04: a rejected OTP is never written to audit in clear")
        void otp_never_audited_in_clear() {
            linked();
            when(reservaQueryService.listForUser(USER_ID))
                    .thenReturn(List.of(reserva(RES_ID, USER_ID, "PENDING_CONFIRMATION")));
            when(otpService.peekActiveReservationType(USER_ID, RES_ID))
                    .thenReturn(Optional.of(OtpType.RESERVATION_CONFIRM));
            org.mockito.Mockito.doThrow(OtpVerificationException.invalid())
                    .when(otpService).verifyForReservation(USER_ID, RES_ID, "654321");

            service.handleUpdate("s3cret", update("/confirmar " + REF + " 654321"));

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(auditRecorder).record(eq(TelegramAuditActions.TELEGRAM_COMMAND_REJECTED), eq(USER_ID), details.capture());
            assertThat(details.getValue()).doesNotContain("654321");
        }
    }
}
