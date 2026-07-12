package com.padelpro.pagos.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padelpro.auth.application.service.EncryptionService;
import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.pagos.application.service.IniciarPagoService;
import com.padelpro.pagos.application.service.PagoAuditRecorder;
import com.padelpro.pagos.application.service.PagoQueryService;
import com.padelpro.pagos.application.service.ProcesarWebhookService;
import com.padelpro.pagos.application.service.RedsysConfigService;
import com.padelpro.pagos.application.service.RedsysOrderIdGenerator;
import com.padelpro.pagos.application.service.RedsysProperties;
import com.padelpro.pagos.application.service.RegistrarPagoEfectivoService;
import com.padelpro.pagos.application.service.SimularPagoService;
import com.padelpro.reservas.domain.port.out.PaymentCommandPort;
import com.padelpro.reservas.domain.port.out.ReservationCommandPort;
import com.padelpro.reservas.infrastructure.persistence.PaymentJpaRepository;
import com.padelpro.reservas.infrastructure.persistence.ReservationJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the pagos-redsys-online capability. Application services are plain objects instantiated
 * here (mirroring {@code ReservasConfig}), keeping the application layer free of Spring stereotypes.
 */
@Configuration
public class PagosConfig {

    @Bean
    public RedsysProperties redsysProperties(
            @Value("${app.redsys.tpv-url:https://sis-t.redsys.es:25443/sis/realizarPago}") String tpvUrl,
            @Value("${app.redsys.merchant-url:http://localhost:8080/api/pagos/webhook}") String merchantUrl,
            @Value("${app.redsys.url-ok:http://localhost:5173/pago/confirmado}") String urlOk,
            @Value("${app.redsys.url-ko:http://localhost:5173/pago/confirmado}") String urlKo,
            @Value("${app.redsys.currency:978}") String currency) {
        return new RedsysProperties(tpvUrl, merchantUrl, urlOk, urlKo, currency);
    }

    @Bean
    public RedsysOrderIdGenerator redsysOrderIdGenerator() {
        return new RedsysOrderIdGenerator();
    }

    @Bean
    public RedsysConfigService redsysConfigService(SystemConfigRepositoryPort systemConfigRepositoryPort,
                                                   EncryptionService encryptionService) {
        return new RedsysConfigService(systemConfigRepositoryPort, encryptionService);
    }

    @Bean
    public PagoAuditRecorder pagoAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                               UserRepositoryPort userRepositoryPort) {
        return new PagoAuditRecorder(auditLogRepositoryPort, userRepositoryPort);
    }

    @Bean
    public IniciarPagoService iniciarPagoService(ReservationCommandPort reservationCommandPort,
                                                 PaymentCommandPort paymentCommandPort,
                                                 RedsysConfigService redsysConfigService,
                                                 RedsysProperties redsysProperties,
                                                 RedsysOrderIdGenerator redsysOrderIdGenerator,
                                                 PagoAuditRecorder pagoAuditRecorder,
                                                 ObjectMapper objectMapper) {
        return new IniciarPagoService(reservationCommandPort, paymentCommandPort, redsysConfigService,
                redsysProperties, redsysOrderIdGenerator, pagoAuditRecorder, objectMapper);
    }

    @Bean
    public ProcesarWebhookService procesarWebhookService(PaymentCommandPort paymentCommandPort,
                                                         ReservationCommandPort reservationCommandPort,
                                                         RedsysConfigService redsysConfigService,
                                                         PagoAuditRecorder pagoAuditRecorder,
                                                         ObjectMapper objectMapper,
                                                         ApplicationEventPublisher eventPublisher) {
        return new ProcesarWebhookService(paymentCommandPort, reservationCommandPort,
                redsysConfigService, pagoAuditRecorder, objectMapper, eventPublisher);
    }

    @Bean
    public RegistrarPagoEfectivoService registrarPagoEfectivoService(
            ReservationCommandPort reservationCommandPort,
            PaymentCommandPort paymentCommandPort,
            PagoAuditRecorder pagoAuditRecorder,
            ApplicationEventPublisher eventPublisher) {
        return new RegistrarPagoEfectivoService(reservationCommandPort, paymentCommandPort,
                pagoAuditRecorder, eventPublisher);
    }

    @Bean
    public PagoQueryService pagoQueryService(PaymentJpaRepository paymentRepository,
                                             ReservationJpaRepository reservationRepository,
                                             UserRepositoryPort userRepositoryPort) {
        return new PagoQueryService(paymentRepository, reservationRepository, userRepositoryPort);
    }

    /**
     * Randomness source for the payment simulator. A {@link java.security.SecureRandom} (a
     * {@link java.util.random.RandomGenerator}) is a CSPRNG — never {@code Math.random()} (OWASP A02).
     * Tests inject a seeded generator for determinism.
     */
    @Bean
    public java.util.random.RandomGenerator simuladorRandomGenerator() {
        return new java.security.SecureRandom();
    }

    @Bean
    public SimularPagoService simularPagoService(ReservationCommandPort reservationCommandPort,
                                                 PaymentCommandPort paymentCommandPort,
                                                 PagoAuditRecorder pagoAuditRecorder,
                                                 ApplicationEventPublisher eventPublisher,
                                                 java.util.random.RandomGenerator simuladorRandomGenerator) {
        return new SimularPagoService(reservationCommandPort, paymentCommandPort, pagoAuditRecorder,
                eventPublisher, simuladorRandomGenerator);
    }
}
