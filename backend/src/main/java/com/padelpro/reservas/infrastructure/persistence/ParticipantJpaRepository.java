package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Participant} (data-model §3.4). Used by the join path to
 * insert a single participant into an existing reservation (capability partidas, D2).
 */
@Repository
public interface ParticipantJpaRepository extends JpaRepository<Participant, Long> {
}
