package com.padelpro.notificaciones.infrastructure.config;

import com.padelpro.notificaciones.application.service.EmailNotificationService;
import com.padelpro.notificaciones.domain.port.out.NotificationLogPort;
import com.padelpro.notificaciones.domain.port.out.NotificationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
