package com.padelpro.reservas.infrastructure.config;

import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.reservas.application.service.AdminReservaService;
import com.padelpro.reservas.application.service.CancelarReservaService;
import com.padelpro.reservas.application.service.CrearReservaService;
import com.padelpro.reservas.application.service.DisponibilidadCacheInvalidator;
import com.padelpro.reservas.application.service.ReservaQueryService;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the reservas write/query use cases (capability reservas, US-007).
 *
 * <p>Mirrors {@code DisponibilidadConfig}/{@code SystemConfigConfig}: application services are plain
 * objects instantiated here so the application layer stays free of Spring stereotypes.
 */
@Configuration
public class ReservasConfig {

    @Bean
    public CrearReservaService crearReservaService(
            ReservationCommandPort reservationCommandPort,
            PaymentCommandPort paymentCommandPort,
            SystemConfigRepositoryPort systemConfigRepositoryPort,
            @Qualifier("disponibilidadCacheInvalidator") DisponibilidadCacheInvalidator cacheInvalidator) {
        return new CrearReservaService(reservationCommandPort, paymentCommandPort,
                systemConfigRepositoryPort, cacheInvalidator);
    }

    @Bean
    public CancelarReservaService cancelarReservaService(
            ReservationCommandPort reservationCommandPort,
            PaymentCommandPort paymentCommandPort,
            SystemConfigRepositoryPort systemConfigRepositoryPort,
            @Qualifier("disponibilidadCacheInvalidator") DisponibilidadCacheInvalidator cacheInvalidator) {
        return new CancelarReservaService(reservationCommandPort, paymentCommandPort,
                systemConfigRepositoryPort, cacheInvalidator);
    }

    @Bean
    public AdminReservaService adminReservaService(
            ReservationCommandPort reservationCommandPort,
            PaymentCommandPort paymentCommandPort,
            @Qualifier("disponibilidadCacheInvalidator") DisponibilidadCacheInvalidator cacheInvalidator) {
        return new AdminReservaService(reservationCommandPort, paymentCommandPort, cacheInvalidator);
    }

    @Bean
    public ReservaQueryService reservaQueryService(
            ReservationJpaRepository reservationRepository,
            PaymentJpaRepository paymentRepository) {
        return new ReservaQueryService(reservationRepository, paymentRepository);
    }
}
