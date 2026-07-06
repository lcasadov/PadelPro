package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.EfectivoPagoResponse;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.exception.PagoNotFoundException;
import com.padelpro.pagos.domain.exception.PagoUnprocessableException;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Register a manual cash payment by an ADMIN (pagos-redsys-online, group 5).
 *
 * <p>The reservation must exist (404) and its payment must not already be PAID (422). On success the
 * payment moves to PAID / CASH with the registering admin id (DB CHECK {@code chk_pay_cash_admin}) and
 * {@code PAYMENT_CASH_REGISTERED} is audited. ADMIN authorization is enforced by the security layer
 * ({@code /api/admin/**} + {@code @PreAuthorize}); a USER gets 403 before reaching this service.
 */
public class RegistrarPagoEfectivoService {

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final PagoAuditRecorder auditRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrarPagoEfectivoService(ReservationCommandPort reservationCommandPort,
                                        PaymentCommandPort paymentCommandPort,
                                        PagoAuditRecorder auditRecorder,
                                        ApplicationEventPublisher eventPublisher) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.auditRecorder = auditRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EfectivoPagoResponse registrar(UUID reservaId, Long adminId) {
        Reservation reservation = reservationCommandPort.findById(reservaId)
                .orElseThrow(() -> new PagoNotFoundException("Reserva no encontrada: " + reservaId));

        Payment payment = paymentCommandPort.findByReservationId(reservation.getId())
                .orElseThrow(() -> new PagoNotFoundException(
                        "No existe un pago para la reserva: " + reservaId));

        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new PagoUnprocessableException("ALREADY_PAID",
                    "La reserva ya está cobrada");
        }

        payment.markCashPaid(adminId, OffsetDateTime.now());
        Payment saved = paymentCommandPort.save(payment);

        auditRecorder.record(PagoAuditActions.PAYMENT_CASH_REGISTERED, adminId,
                "reservationId=" + reservaId + ", paymentId=" + saved.getId());

        // Notification trigger (change notificaciones-eventos-email, D1): receipt email to the titular.
        // Published inside the tx; delivered post-commit. Reference is the payment id (no card data).
        String reference = saved.getId() != null ? saved.getId().toString() : null;
        eventPublisher.publishEvent(new PaymentPaidEmailEvent(
                reservation.getId(), reservation.getOwnerId(), saved.getAmount(),
                saved.getPaidAt(), reference));

        return new EfectivoPagoResponse(
                saved.getId() != null ? saved.getId().toString() : null,
                reservaId.toString(),
                saved.getAmount(),
                saved.getMethod() != null ? saved.getMethod().name() : null,
                saved.getStatus().name(),
                saved.getRegisteredById(),
                saved.getPaidAt());
    }
}
