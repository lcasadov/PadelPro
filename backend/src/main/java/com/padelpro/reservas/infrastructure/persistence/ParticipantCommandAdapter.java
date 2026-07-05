package com.padelpro.reservas.infrastructure.persistence;

import com.padelpro.reservas.domain.model.Participant;
import com.padelpro.reservas.domain.port.out.ParticipantCommandPort;
import org.springframework.stereotype.Component;

/**
 * Write adapter that inserts a single {@link Participant} into an existing reservation (capability
 * partidas, D2). {@code saveAndFlush} on a new participant issues an INSERT (not a merge), so the
 * generated identity is populated on the returned instance and surfaced in {@code UnirseResponse}.
 */
@Component
public class ParticipantCommandAdapter implements ParticipantCommandPort {

    private final ParticipantJpaRepository participantRepository;

    public ParticipantCommandAdapter(ParticipantJpaRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    @Override
    public Participant save(Participant participant) {
        return participantRepository.saveAndFlush(participant);
    }
}
