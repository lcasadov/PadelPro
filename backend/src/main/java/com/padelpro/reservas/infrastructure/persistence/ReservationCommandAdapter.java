package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.exception.SlotConflictException;
import com.padelpro.reservas.domain.model.IdempotencyKey;
import com.padelpro.reservas.domain.model.Reservation;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Write adapter for reservations (capability reservas, US-007).
 *
 * <p>Design D1: instead of {@code SELECT FOR UPDATE}, the INSERT is attempted and the PostgreSQL
 * {@code excl_res_no_overlap} gist exclusion violation is caught and translated to
 * {@link SlotConflictException} (→ HTTP 409). The constraint name is matched defensively in the
 * exception chain so an unrelated integrity error is not masked as a slot conflict.
 */
@Component
public class ReservationCommandAdapter implements ReservationCommandPort {

    private static final String OVERLAP_CONSTRAINT = "excl_res_no_overlap";

    private final ReservationJpaRepository reservationRepository;
    private final IdempotencyKeyJpaRepository idempotencyKeyRepository;

    public ReservationCommandAdapter(ReservationJpaRepository reservationRepository,
                                     IdempotencyKeyJpaRepository idempotencyKeyRepository) {
        this.reservationRepository = reservationRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        try {
            // flush so the gist violation surfaces here (inside this call) rather than at commit.
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new SlotConflictException("La franja horaria solicitada ya está ocupada");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return reservationRepository.findByIdWithParticipants(id);
    }

    @Override
    public IdempotencyKey saveIdempotencyKey(IdempotencyKey key) {
        return idempotencyKeyRepository.saveAndFlush(key);
    }

    @Override
    public Optional<IdempotencyKey> findIdempotencyKey(Long userId, String key) {
        return idempotencyKeyRepository.findByUserIdAndIdemKey(userId, key);
    }

    /** True if the integrity violation was caused by the anti-overlap gist exclusion constraint. */
    private boolean isOverlapViolation(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains(OVERLAP_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
