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
 */
final class ReservaMapper {

    private ReservaMapper() {
    }

    static ReservaResponse toResponse(Reservation r, Payment payment) {
        List<ParticipanteResponse> participants = r.getParticipants().stream()
                .map(p -> new ParticipanteResponse(
                        p.getUserId(),
                        p.getExternalName(),
                        p.getExternalPhone(),
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
                r.getNotes(),
                priceTotal,
                participants,
                pago,
                r.getCreatedAt());
    }
}
