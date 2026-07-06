package com.padelpro.notificaciones.infrastructure.persistence;

import com.padelpro.notificaciones.domain.model.NotificationLog;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for {@link NotificationLogPort}, backed by
 * {@link NotificationLogJpaRepository} (change {@code notificaciones-eventos-email}, D2).
 */
@Component
public class NotificationLogAdapter implements NotificationLogPort {

    private final NotificationLogJpaRepository repository;

    public NotificationLogAdapter(NotificationLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationLog save(NotificationLog entry) {
        return repository.save(entry);
    }

    @Override
    public Optional<NotificationLog> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<NotificationLog> findRetriable(int maxAttempts) {
        return repository.findRetriable(maxAttempts);
    }
}
