package com.padelpro.reservas.application.service;

import com.padelpro.reservas.application.dto.PagoResponse;
import com.padelpro.reservas.application.dto.ParticipanteResponse;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Maps domain {@link Reservation}/{@link Payment} aggregates to API DTOs (capability reservas).
 *
 * <p><b>PII minimization (RN-RGPD-03).</b> A reservation detail/list is visible not only to its owner
 * but also to co-players who joined an open match (capability partidas, D2). Since anyone can join an
 * open match, guest phone numbers ({@code externalPhone}) and the reservation {@code notes} must NOT
 * be exposed to a mere co-participant. Only the owner and ADMIN see those fields; for everyone else
 * they are nulled out. This shapes the response only — it does not change the 403 access rule.
 */
final class ReservaMapper {

    private ReservaMapper() {
    }

    /**
     * @param includePii when {@code false} (requester is a co-participant, not owner nor ADMIN),
     *                   {@code externalPhone} of every participant and the reservation {@code notes}
     *                   are nulled out (RN-RGPD-03).
     */
    static ReservaResponse toResponse(Reservation r, Payment payment, boolean includePii) {
        List<ParticipanteResponse> participants = r.getParticipants().stream()
                .map(p -> new ParticipanteResponse(
                        p.getUserId(),
                        p.getExternalName(),
                        includePii ? p.getExternalPhone() : null,
                        p.getSlotPosition(),
                        p.isOwner()))
                .sorted((a, b) -> Integer.compare(a.slotPosition(), b.slotPosition()))
                .toList();

        BigDecimal priceTotal = payment != null ? payment.getAmount() : null;
        PagoResponse pago = payment == null ? null : new PagoResponse(
                payment.getId() != null ? payment.getId().toString() : null,
                payment.getAmount(),
                payment.getMethod() != null ? payment.getMethod().name() : null,
                payment.getStatus() != null ? payment.getStatus().name() : null,
                payment.getPaidAt());

        return new ReservaResponse(
                r.getId() != null ? r.getId().toString() : null,
                r.getOwnerId(),
                r.getReservationDate(),
                r.getStartTime(),
                r.getEndTime(),
                r.getDurationMinutes(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getChannel() != null ? r.getChannel().name() : null,
                includePii ? r.getNotes() : null,
                priceTotal,
                participants,
                pago,
                r.getCreatedAt());
    }
}
