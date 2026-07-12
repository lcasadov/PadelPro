package com.padelpro.notificaciones.infrastructure.config;

import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.mensajeria.domain.port.out.TelegramPort;
import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.application.service.NotificationLogPurgeService;
import com.padelpro.notificaciones.application.service.TelegramNotificationService;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import com.padelpro.notificaciones.infrastructure.persistence.NotificationLogJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Wiring for the notificaciones event-email use cases (change {@code notificaciones-eventos-email}).
 *
 * <p>Mirrors {@code ReservasConfig}/{@code PagosConfig}: the application service is a plain object
 * instantiated here so the application layer stays free of Spring stereotypes. The {@code @Async}
 * behaviour of {@link EmailNotificationService#dispatch} still applies because Spring wraps the bean
 * in an async proxy regardless of how it is registered.
 */
@Configuration
public class NotificacionesConfig {

    @Bean
    public EmailNotificationService emailNotificationService(NotificationLogPort notificationLogPort,
                                                             NotificationPort notificationPort) {
        return new EmailNotificationService(notificationLogPort, notificationPort);
    }

    @Bean
    public TelegramNotificationService telegramNotificationService(
            NotificationLogPort notificationLogPort,
            TelegramPort telegramPort,
            SystemConfigRepositoryPort systemConfigRepositoryPort) {
        return new TelegramNotificationService(notificationLogPort, telegramPort,
                systemConfigRepositoryPort);
    }

    /**
     * Retention purge for {@code notification_log} (#199). The retention window comes from
     * {@code app.notification.retention} (ISO-8601 duration, default {@code P180D} = 6 months);
     * parsed explicitly with {@link Duration#parse} so the wiring never depends on the ambient
     * {@code @Value} conversion service, mirroring {@code IdempotencyKeyPurgeService}.
     */
    @Bean
    public NotificationLogPurgeService notificationLogPurgeService(
            NotificationLogJpaRepository notificationLogRepository,
            @Value("${app.notification.retention:P180D}") String retentionIso) {
        return new NotificationLogPurgeService(
                notificationLogRepository, Duration.parse(retentionIso), Clock.systemUTC());
    }
}
