package com.padelpro.reservas.infrastructure.config;

import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.bloqueos.domain.port.out.BloqueoQueryPort;
import com.padelpro.reservas.application.service.DisponibilidadCacheInvalidator;
import com.padelpro.reservas.application.service.DisponibilidadService;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the disponibilidad-pistas capability.
 *
 * <p>Instantiates {@link DisponibilidadService} explicitly (mirroring {@code SystemConfigConfig})
 * so the application layer stays free of Spring stereotypes. The same instance is exposed under the
 * {@link DisponibilidadCacheInvalidator} type so Wave 3 can depend on the narrow invalidation hook.
 */
@Configuration
public class DisponibilidadConfig {

    @Bean
    public DisponibilidadService disponibilidadService(
            ReservationQueryPort reservationQueryPort,
            SystemConfigRepositoryPort systemConfigRepositoryPort,
            BloqueoQueryPort bloqueoQueryPort) {
        return new DisponibilidadService(reservationQueryPort, systemConfigRepositoryPort, bloqueoQueryPort);
    }

    @Bean
    public DisponibilidadCacheInvalidator disponibilidadCacheInvalidator(
            DisponibilidadService disponibilidadService) {
        return disponibilidadService;
    }
}
