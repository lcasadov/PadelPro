package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Payment;
import com.padelpro.reservas.domain.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Payment} (data-model §3.5).
 *
 * <p>1:1 with {@code reservations}; {@link #findByReservationId(UUID)} backs the refund path on
 * cancellation. List queries that join payments to reservations live in {@link ReservationJpaRepository}.
 */
@Repository
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReservationId(UUID reservationId);

    /** Batch-load payments for a set of reservations (anti-N+1 for list endpoints, §7.2). */
    List<Payment> findByReservationIdIn(Collection<UUID> reservationIds);

    /** Locate a payment by its unique Redsys order id (webhook idempotency, RN-PAY-02). */
    Optional<Payment> findByRedsysOrderId(String redsysOrderId);

    /**
     * Locate a payment by Redsys order id locking its row ({@code SELECT ... FOR UPDATE}) so two
     * concurrent webhook notifications for the same order cannot both transition IN_PROGRESS → PAID
     * (MEDIO-2). The second waits for the first to commit, then re-reads the already-PAID state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.redsysOrderId = :redsysOrderId")
    Optional<Payment> findByRedsysOrderIdForUpdate(@Param("redsysOrderId") String redsysOrderId);

    /** All payments most-recent first (ADMIN history, pagos-redsys-online group 5). */
    List<Payment> findAllByOrderByCreatedAtDesc();

    /**
     * Income grouped by payment method for the admin dashboard (administracion-club, RN-ADM-03):
     * {@code SUM(amount)} of PAID payments whose {@code paid_at} falls in the half-open range
     * {@code [start, end)}. Aggregated in the database; returns one {@code Object[]{PaymentMethod,
     * BigDecimal}} row per method that has at least one PAID payment.
     */
    @Query("""
            SELECT p.method, COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.status = :paid
              AND p.paidAt >= :start AND p.paidAt < :end
            GROUP BY p.method
            """)
    List<Object[]> sumPaidAmountGroupedByMethod(@Param("start") OffsetDateTime start,
                                                @Param("end") OffsetDateTime end,
                                                @Param("paid") PaymentStatus paid);

    /**
     * Minimal PAID payment rows {@code (paid_at, amount, method)} in {@code [start, end)} for the
     * per-day usage report (administracion-club CSV). No card, gateway or customer data is selected
     * (RN-RGPD-04).
     */
    @Query("""
            SELECT p.paidAt, p.amount, p.method
            FROM Payment p
            WHERE p.status = :paid
              AND p.paidAt >= :start AND p.paidAt < :end
            """)
    List<Object[]> findPaidRowsInRange(@Param("start") OffsetDateTime start,
                                       @Param("end") OffsetDateTime end,
                                       @Param("paid") PaymentStatus paid);
}
