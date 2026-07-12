package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link IdempotencyKey} (design D2).
 *
 * <p>Lookup is by the per-user uniqueness pair {@code (user_id, idem_key)}.
 */
@Repository
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndIdemKey(Long userId, String idemKey);

    /**
     * Bulk-deletes idempotency records created strictly before {@code threshold}, returning the number
     * of rows removed. An {@code Idempotency-Key} only guards short-window client retries, so rows past
     * the retention window (V9 comment: "a deferred job purges keys older than the retention window")
     * are dead weight and unbounded growth. Backed by {@code idx_idem_created_at} (V9).
     *
     * <p>{@code @Modifying} DELETE — requires an active transaction, provided by the calling service.
     * Rows still inside the TTL window ({@code created_at >= threshold}) are preserved.
     *
     * @param threshold cutoff instant ({@code now - ttl}); rows with {@code created_at <} this are purged
     * @return count of purged rows
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") OffsetDateTime threshold);
}
