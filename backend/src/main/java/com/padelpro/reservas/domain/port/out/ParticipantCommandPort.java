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
}
