package com.padelpro.reservas.domain.port.out;

import com.padelpro.reservas.domain.model.Participant;

/**
 * Outbound write port to add a single participant to an already-persisted reservation (capability
 * partidas, D2). The create path persists a whole reservation aggregate at once; joining an existing
 * reservation instead inserts one participant row, so its generated id is available on the returned
 * instance (a whole-aggregate {@code merge} would otherwise leave the caller's reference id-less).
 */
public interface ParticipantCommandPort {

    /**
     * Persist a new participant (its {@code reservation} association must already be set). Returns the
     * managed instance with its generated {@code id} populated.
     */
    Participant save(Participant participant);

    /**
     * Anonymize the participation rows of a given user for the RGPD right-to-be-forgotten flow
     * (capability exportaciones-rgpd): overwrite {@code external_name = 'ANONIMIZADO'} and
     * {@code external_phone = NULL} for every {@code participants} row where {@code user_id = :userId}.
     * The {@code user_id} is preserved (Requirement 2, Scenario 4 — the anonymized user still exists
     * as an entity; FK SET NULL does not apply here).
     *
     * <p>Idempotent: safe to re-run on an already-anonymized user.
     *
     * @param userId the id of the anonymized user whose participation rows are anonymized
     */
    void anonymizeByUserId(Long userId);
}
