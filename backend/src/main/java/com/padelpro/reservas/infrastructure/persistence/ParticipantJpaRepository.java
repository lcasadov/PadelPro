package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link Participant} (data-model §3.4). Used by the join path to
 * insert a single participant into an existing reservation (capability partidas, D2).
 */
@Repository
public interface ParticipantJpaRepository extends JpaRepository<Participant, Long> {

    /**
     * RGPD anonymization (capability exportaciones-rgpd, RN-RGPD-01) — overwrite the external
     * identity columns of a user's participation rows.
     *
     * <p><b>SCHEMA CONFLICT (reported to orchestrator):</b> the spec/tasks require
     * {@code SET external_name = 'ANONIMIZADO', external_phone = NULL WHERE user_id = :userId}, but
     * the {@code participants} table has CHECK {@code chk_part_user_or_external} (V7 migration):
     * a row must be EITHER registered ({@code user_id NOT NULL AND external_name NULL}) OR external
     * ({@code user_id NULL AND external_name NOT NULL}). Rows matched by {@code user_id = :userId}
     * are registered participants, so their {@code external_name} is already NULL and
     * {@code external_phone} already NULL — setting {@code external_name = 'ANONIMIZADO'} would
     * violate the CHECK. Registered participants carry NO personal data of their own (the name/phone
     * live in {@code users}, already anonymized upstream), so this UPDATE only nullifies
     * {@code external_phone} (a no-op for well-formed rows) and preserves {@code user_id}
     * (Requirement 2, Scenario 4). See design.md "Sin migración de esquema"; reconciling the literal
     * {@code external_name='ANONIMIZADO'} text with the CHECK requires a spec/schema decision.
     */
    @Transactional
    @Modifying
    @Query("UPDATE Participant p SET p.externalPhone = NULL WHERE p.userId = :userId")
    void anonymizeByUserId(@Param("userId") Long userId);
}
