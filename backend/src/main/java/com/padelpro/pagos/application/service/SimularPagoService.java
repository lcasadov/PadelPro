package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.SimularPagoRequest;
import com.padelpro.pagos.application.dto.SimularPagoResponse;
import com.padelpro.pagos.domain.audit.PagoAuditActions;
import com.padelpro.pagos.domain.exception.PagoConflictException;
import com.padelpro.pagos.domain.exception.PagoForbiddenException;
import com.padelpro.pagos.domain.exception.PagoNotFoundException;
import com.padelpro.pagos.domain.exception.PagoValidationException;
import com.padelpro.notificaciones.domain.event.PaymentPaidEmailEvent;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Provisional online payment simulator (change pagos-simulador-gestion, D2). Stands in for the real
 * Redsys TPV until credentials are configured; {@link IniciarPagoService} is untouched and remains the
 * definitive path.
 *
 * <p>Flow of {@code POST /api/pagos/simular}:
 * <ol>
 *   <li>Validate the card fields' <em>format</em> (400 {@code VALIDATION_ERROR}) — length 13–19 digits,
 *       {@code MM/AA} with month 01–12 and not expired, CVC exactly 3 digits.</li>
 *   <li>The reservation must exist AND be owned by the caller, else 403 (existence is not leaked).</li>
 *   <li>The payment must be PENDING; an already-PAID one is a 409 idempotent rejection (no re-charge).</li>
 *   <li>Decide the outcome: magic {@link #MAGIC_APPROVE} → always APPROVED; {@link #MAGIC_DECLINE} →
 *       always DECLINED; any other card → {@link #APPROVE_THRESHOLD}% APPROVED via the injected
 *       {@link RandomGenerator} (seedable for deterministic tests).</li>
 *   <li>APPROVED → the payment moves to PAID / SIMULADO (same confirmation transition family as cash
 *       and webhook) and a receipt email event is published. DECLINED → the payment stays PENDING.</li>
 * </ol>
 *
 * <p>RN-RGPD-04: the card number and CVC are used only in memory to decide the result and are then
 * discarded — never persisted and never written to the audit log.
 */
public class SimularPagoService {

    /** Magic card that ALWAYS approves (deterministic tests / E2E). */
    static final String MAGIC_APPROVE = "4111111111111111";
    /** Magic card that ALWAYS declines (deterministic tests / E2E). */
    static final String MAGIC_DECLINE = "4000000000000002";
    /** Approval probability for non-magic cards: {@code nextInt(100) < 85} ≈ 85%. */
    static final int APPROVE_THRESHOLD = 85;

    private static final String DECLINE_REASON =
            "Pago rechazado por la entidad emisora (simulado). Prueba con otra tarjeta.";

    private static final Pattern CARD_PATTERN = Pattern.compile("\\d{13,19}");
    private static final Pattern CVC_PATTERN = Pattern.compile("\\d{3}");
    private static final Pattern EXPIRY_PATTERN = Pattern.compile("(0[1-9]|1[0-2])/\\d{2}");

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final PagoAuditRecorder auditRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final RandomGenerator random;

    public SimularPagoService(ReservationCommandPort reservationCommandPort,
                              PaymentCommandPort paymentCommandPort,
                              PagoAuditRecorder auditRecorder,
                              ApplicationEventPublisher eventPublisher,
                              RandomGenerator random) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.auditRecorder = auditRecorder;
        this.eventPublisher = eventPublisher;
        this.random = random;
    }

    @Transactional
    public SimularPagoResponse simular(SimularPagoRequest request, Long currentUserId) {
        // 1. Format validation first (cheap, no DB, no existence leak). Card data stays in memory.
        String card = normalizeCard(request.cardNumber());
        validateFormat(card, request.expiry(), request.cvc());

        // 2. Owner-only: a missing reservation and a non-owned one both surface as 403 (no leak).
        UUID reservaId = request.reservaId();
        Reservation reservation = (reservaId == null)
                ? null
                : reservationCommandPort.findById(reservaId).orElse(null);
        if (reservation == null || !reservation.getOwnerId().equals(currentUserId)) {
            throw new PagoForbiddenException("No puedes pagar esta reserva");
        }

        Payment payment = paymentCommandPort.findByReservationId(reservaId)
                .orElseThrow(() -> new PagoNotFoundException(
                        "No existe un pago para la reserva: " + reservaId));

        // 3. Idempotency: never re-charge an already-paid reservation.
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new PagoConflictException("La reserva ya está pagada");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PagoConflictException("El pago no admite un cobro simulado en su estado actual");
        }

        // 4. Decide the outcome (magic cards deterministic, otherwise ~85% approve).
        if (!approves(card)) {
            auditRecorder.record(PagoAuditActions.PAYMENT_SIMULATED_DECLINED, currentUserId,
                    "reservationId=" + reservaId);
            return SimularPagoResponse.declined(DECLINE_REASON);
        }

        // 5. APPROVED → PAID / SIMULADO (no card data reaches the entity, RN-RGPD-04).
        payment.markSimulatedPaid(OffsetDateTime.now());
        Payment saved = paymentCommandPort.save(payment);

        auditRecorder.record(PagoAuditActions.PAYMENT_SIMULATED_APPROVED, currentUserId,
                "reservationId=" + reservaId + ", paymentId=" + saved.getId());

        // Notification trigger (change notificaciones-eventos-email, D1): receipt email to the titular.
        // Published inside the tx, delivered post-commit. Reference is the payment id — never card data.
        String reference = saved.getId() != null ? saved.getId().toString() : null;
        eventPublisher.publishEvent(new PaymentPaidEmailEvent(
                reservation.getId(), reservation.getOwnerId(), saved.getAmount(),
                saved.getPaidAt(), reference));

        return SimularPagoResponse.approved();
    }

    /** Strip every whitespace character; nulls become empty so validation fails cleanly. */
    private String normalizeCard(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replaceAll("\\s", "");
    }

    private void validateFormat(String normalizedCard, String expiry, String cvc) {
        if (!CARD_PATTERN.matcher(normalizedCard).matches()) {
            throw new PagoValidationException("Número de tarjeta inválido");
        }
        if (cvc == null || !CVC_PATTERN.matcher(cvc).matches()) {
            throw new PagoValidationException("CVC inválido");
        }
        if (expiry == null || !EXPIRY_PATTERN.matcher(expiry).matches()) {
            throw new PagoValidationException("Caducidad inválida");
        }
        int month = Integer.parseInt(expiry.substring(0, 2));
        int year = 2000 + Integer.parseInt(expiry.substring(3, 5));
        // A card is valid through the end of its expiry month; expired if that month is already past.
        if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            throw new PagoValidationException("La tarjeta está caducada");
        }
    }

    /** Decision: magic cards are deterministic; any other card approves ~85% of the time. */
    private boolean approves(String normalizedCard) {
        if (MAGIC_APPROVE.equals(normalizedCard)) {
            return true;
        }
        if (MAGIC_DECLINE.equals(normalizedCard)) {
            return false;
        }
        return random.nextInt(100) < APPROVE_THRESHOLD;
    }
}
