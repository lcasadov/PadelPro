package com.padelpro.administracion.infrastructure.config;

import com.padelpro.administracion.application.service.DashboardService;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the administracion-club capability. The application service is a plain object
 * instantiated here (mirroring {@code PagosConfig}), keeping the application layer free of Spring
 * stereotypes.
 */
@Configuration
public class AdministracionConfig {

    @Bean
    public DashboardService dashboardService(PaymentJpaRepository paymentRepository,
                                             ReservationJpaRepository reservationRepository,
                                             SystemConfigRepositoryPort systemConfigRepositoryPort) {
        return new DashboardService(paymentRepository, reservationRepository, systemConfigRepositoryPort);
    }
}
