package com.padelpro.otp.infrastructure.config;

import com.padelpro.auth.domain.port.out.AuditLogRepositoryPort;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.otp.application.service.OtpAuditRecorder;
import com.padelpro.otp.application.service.OtpService;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the auth-otp-telegram OTP use cases. Application services are plain objects
 * instantiated here (mirrors {@code PagosConfig}/{@code ReservasConfig}), keeping the application
 * layer free of Spring stereotypes.
 */
@Configuration
public class OtpConfig {

    @Bean
    public OtpAuditRecorder otpAuditRecorder(AuditLogRepositoryPort auditLogRepositoryPort,
                                             UserRepositoryPort userRepositoryPort) {
        return new OtpAuditRecorder(auditLogRepositoryPort, userRepositoryPort);
    }

    @Bean
    public OtpService otpService(OtpCodeRepositoryPort otpCodeRepositoryPort,
                                 OtpAuditRecorder otpAuditRecorder) {
        return new OtpService(otpCodeRepositoryPort, otpAuditRecorder);
    }
}
