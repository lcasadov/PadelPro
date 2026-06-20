package com.padelpro.reservas.application.service;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.domain.exception.InvalidReservaStateException;
import com.padelpro.reservas.domain.exception.ReservaForbiddenException;
import com.padelpro.reservas.domain.exception.ReservaNotFoundException;
import com.padelpro.reservas.domain.model.CancellationPolicy;
import com.padelpro.reservas.domain.model.PaymentStatus;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.model.ReservationStateMachine;
import com.padelpro.reservas.domain.model.ReservationStatus;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cancel-reservation use case for the USER/owner path (capability reservas, US-007, RN-RES-04/AUTH-02).
 *
 * <p>Authorization: only the owner or an ADMIN may cancel (RN-AUTH-02). The deadline policy applies
 * to the USER path — cancelling within {@code cancellation_deadline_hours} refunds a PAID payment
 * (REFUNDED); cancelling after the deadline is rejected with 422 (the ADMIN bypass lives in
 * {@link AdminReservaService}). The state machine forbids cancelling a terminal reservation (422).
 */
public class CancelarReservaService {

    private final ReservationCommandPort reservationCommandPort;
    private final PaymentCommandPort paymentCommandPort;
    private final SystemConfigRepositoryPort systemConfigRepositoryPort;
    private final DisponibilidadCacheInvalidator cacheInvalidator;

    public CancelarReservaService(ReservationCommandPort reservationCommandPort,
                                  PaymentCommandPort paymentCommandPort,
                                  SystemConfigRepositoryPort systemConfigRepositoryPort,
                                  DisponibilidadCacheInvalidator cacheInvalidator) {
        this.reservationCommandPort = reservationCommandPort;
        this.paymentCommandPort = paymentCommandPort;
        this.systemConfigRepositoryPort = systemConfigRepositoryPort;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * @param id      reservation id
     * @param userId  authenticated user id
     * @param admin   whether the caller has ADMIN role (bypasses owner check and deadline policy)
     */
    @Transactional
    public void cancelar(UUID id, Long userId, boolean admin) {
        Reservation reservation = reservationCommandPort.findById(id)
                .orElseThrow(() -> new ReservaNotFoundException(id));

        // RN-AUTH-02 — only owner or ADMIN. Owner check leaks no existence (caller already owns scope).
        if (!admin && !userId.equals(reservation.getOwnerId())) {
            throw new ReservaForbiddenException("Solo el titular puede cancelar la reserva");
        }

        // D6 — transition must be valid (e.g. cannot cancel a CANCELLED/COMPLETED reservation → 422).
        ReservationStateMachine.assertCanTransition(reservation.getStatus(), ReservationStatus.CANCELLED);

        // RN-RES-04 — deadline policy applies to USER path only; ADMIN bypasses (D-RES-03).
        if (!admin) {
            SystemConfig config = systemConfigRepositoryPort.findById(1L)
                    .orElseThrow(() -> new IllegalStateException("System configuration not found"));
            boolean allowed = CancellationPolicy.canUserCancel(
                    reservation.getReservationDate(), reservation.getStartTime(),
                    config.getCancellationDeadlineHours(), LocalDateTime.now());
            if (!allowed) {
                throw new InvalidReservaStateException(
                        "CANCELLATION_DEADLINE_PASSED",
                        "La cancelación está fuera del plazo permitido");
            }
        }

        reservation.changeStatus(ReservationStatus.CANCELLED);
        reservationCommandPort.save(reservation);

        // Refund a PAID payment (RN-RES-04). PENDING/other states are simply left as-is.
        paymentCommandPort.findByReservationId(id).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PAID) {
                payment.markRefunded();
                paymentCommandPort.save(payment);
            }
        });

        cacheInvalidator.invalidate(reservation.getReservationDate());
    }
}
