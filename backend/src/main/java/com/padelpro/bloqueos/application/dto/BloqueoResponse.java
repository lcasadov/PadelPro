package com.padelpro.bloqueos.application.dto;

import com.padelpro.bloqueos.domain.model.BloqueoPista;

import java.time.format.DateTimeFormatter;

/**
 * Response view of a blocked slot (change bloqueos-pista-eventos, D6). {@code hora} is formatted as
 * {@code HH:mm} for a stable API contract, mirroring the availability response.
 *
 * @param id     block identifier (used by {@code DELETE /api/admin/bloqueos/{id}})
 * @param fecha  blocked date, ISO {@code yyyy-MM-dd}
 * @param hora   blocked slot-start hour, {@code HH:mm}
 * @param motivo reason recorded by the admin (may be {@code null})
 */
public record BloqueoResponse(Long id, String fecha, String hora, String motivo) {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** Maps a persisted {@link BloqueoPista} to its response view. */
    public static BloqueoResponse from(BloqueoPista bloqueo) {
        return new BloqueoResponse(
                bloqueo.getId(),
                bloqueo.getFecha().toString(),
                bloqueo.getHora().format(TIME_FMT),
                bloqueo.getMotivo());
    }
}
