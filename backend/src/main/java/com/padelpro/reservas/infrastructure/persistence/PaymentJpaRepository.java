package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
