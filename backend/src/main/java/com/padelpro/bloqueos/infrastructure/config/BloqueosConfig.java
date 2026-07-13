package com.padelpro.bloqueos.infrastructure.config;

import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.bloqueos.application.service.BloqueoAuditRecorder;
import com.padelpro.bloqueos.application.service.BloqueoService;
import com.padelpro.bloqueos.infrastructure.persistence.BloqueoJpaRepository;
import com.padelpro.reservas.application.service.DisponibilidadCacheInvalidator;
import com.padelpro.reservas.domain.port.out.ReservationQueryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the bloqueos-pista-eventos change. Application services are plain objects instantiated
 * here (mirroring {@code DisponibilidadConfig} / {@code PagosConfig}), keeping the application layer
 * free of Spring stereotypes.
 */
@Configuration
public class BloqueosConfig {

    @Bean
    public BloqueoAuditRecorder bloqueoAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                                     UserRepositoryPort userRepositoryPort) {
        return new BloqueoAuditRecorder(auditLogRepositoryPort, userRepositoryPort);
    }

    @Bean
    public BloqueoService bloqueoService(BloqueoJpaRepository bloqueoJpaRepository,
                                         ReservationQueryPort reservationQueryPort,
                                         DisponibilidadCacheInvalidator disponibilidadCacheInvalidator,
                                         BloqueoAuditRecorder bloqueoAuditRecorder) {
        return new BloqueoService(bloqueoJpaRepository, reservationQueryPort,
                disponibilidadCacheInvalidator, bloqueoAuditRecorder);
    }
}
