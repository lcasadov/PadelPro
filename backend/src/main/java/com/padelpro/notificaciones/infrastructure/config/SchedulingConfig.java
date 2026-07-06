package com.padelpro.notificaciones.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} support for the {@code notificaciones} capability (change
 * {@code notificaciones-eventos-email}, Req 3 / D3) — the failed-email retry job runs on a schedule.
 *
 * <p>The project had no scheduler enabled before this change; this is the single activation point.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
