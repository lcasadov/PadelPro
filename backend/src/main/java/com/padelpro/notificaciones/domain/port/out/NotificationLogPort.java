package com.padelpro.notificaciones.domain.port.out;

import com.padelpro.notificaciones.domain.model.NotificationLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting {@link NotificationLog} entries (change
 * {@code notificaciones-eventos-email}, D2).
 *
 * <p>The application layer depends on this interface; the JPA adapter lives in
 * {@code notificaciones.infrastructure.persistence} (hexagonal / ports &amp; adapters).
 */
public interface NotificationLogPort {

    /** Persist (insert or update) a notification log entry and return the managed instance. */
    NotificationLog save(NotificationLog entry);

    /** Look up an entry by id. */
    Optional<NotificationLog> findById(UUID id);

    /**
     * Return the {@code FAILED} entries eligible for a retry — those whose {@code attempts} are below
     * {@code maxAttempts} (Req 3). Backoff pacing is applied by the caller (the retry job).
     *
     * @param maxAttempts the attempt cap (retries stop once attempts reach it)
     */
    List<NotificationLog> findRetriable(int maxAttempts);
}
