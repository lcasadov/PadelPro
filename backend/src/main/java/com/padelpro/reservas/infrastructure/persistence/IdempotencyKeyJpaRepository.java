package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link IdempotencyKey} (design D2).
 *
 * <p>Lookup is by the per-user uniqueness pair {@code (user_id, idem_key)}.
 */
@Repository
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndIdemKey(Long userId, String idemKey);
}
