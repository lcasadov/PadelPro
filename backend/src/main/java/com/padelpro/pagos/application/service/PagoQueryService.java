package com.padelpro.pagos.application.service;

import com.padelpro.pagos.application.dto.PagoHistorialResponse;
import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read use cases for payment history (pagos-redsys-online, group 5).
 *
 * <p>{@code GET /api/pagos} returns the authenticated user's own payments (reservations they own);
 * {@code GET /api/admin/pagos} returns every payment. Anti-N+1: reservations are batch-loaded once and
 * joined in memory. Never exposes card data — only {@code redsysOrderId}/{@code transactionId}
 * (RN-PAY-03).
 */
public class PagoQueryService {

    private final PaymentJpaRepository paymentRepository;
    private final ReservationJpaRepository reservationRepository;

    public PagoQueryService(PaymentJpaRepository paymentRepository,
                            ReservationJpaRepository reservationRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
    }

    /** Payments of reservations owned by the user, most-recent first. */
    @Transactional(readOnly = true)
    public List<PagoHistorialResponse> listForUser(Long userId) {
        List<Reservation> owned = reservationRepository.findByOwnerId(userId);
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<UUID, Reservation> byId = owned.stream()
                .collect(Collectors.toMap(Reservation::getId, Function.identity()));
        List<Payment> payments = paymentRepository.findByReservationIdIn(byId.keySet());
        return payments.stream()
                .map(p -> toResponse(p, byId.get(p.getReservationId())))
                .sorted(Comparator.comparing(PagoHistorialResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** All payments (ADMIN), most-recent first. */
    @Transactional(readOnly = true)
    public List<PagoHistorialResponse> listAll() {
        List<Payment> payments = paymentRepository.findAllByOrderByCreatedAtDesc();
        if (payments.isEmpty()) {
            return List.of();
        }
        List<UUID> reservationIds = payments.stream().map(Payment::getReservationId).toList();
        Map<UUID, Reservation> byId = reservationRepository.findAllById(reservationIds).stream()
                .collect(Collectors.toMap(Reservation::getId, Function.identity()));
        return payments.stream()
                .map(p -> toResponse(p, byId.get(p.getReservationId())))
                .toList();
    }

    private PagoHistorialResponse toResponse(Payment p, Reservation r) {
        return new PagoHistorialResponse(
                p.getId() != null ? p.getId().toString() : null,
                p.getReservationId() != null ? p.getReservationId().toString() : null,
                r != null ? r.getOwnerId() : null,
                p.getAmount(),
                p.getMethod() != null ? p.getMethod().name() : null,
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getRedsysOrderId(),
                p.getTransactionId(),
                r != null ? r.getReservationDate() : null,
                r != null ? r.getStartTime() : null,
                p.getPaidAt(),
                p.getCreatedAt());
    }
}
