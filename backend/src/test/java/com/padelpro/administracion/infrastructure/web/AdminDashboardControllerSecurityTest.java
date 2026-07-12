package com.padelpro.administracion.infrastructure.web;

import com.padelpro.administracion.application.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the {@link PreAuthorize} guard on {@link AdminDashboardController} enforces
 * {@code ROLE_ADMIN} (capability administracion-club, RN-ADM-01). A lightweight context with method
 * security enabled proxies the controller; no web layer or database is loaded, so the test runs as a
 * fast unit test (it is not a Testcontainers integration test).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminDashboardControllerSecurityTest.SecurityTestConfig.class)
@DisplayName("AdminDashboardController — RBAC (RN-ADM-01)")
class AdminDashboardControllerSecurityTest {

    @EnableMethodSecurity
    @Configuration
    static class SecurityTestConfig {

        @Bean
        DashboardService dashboardService() {
            return Mockito.mock(DashboardService.class);
        }

        @Bean
        AdminDashboardController adminDashboardController(DashboardService dashboardService) {
            return new AdminDashboardController(dashboardService);
        }
    }

    @Autowired
    private AdminDashboardController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("un USER recibe AccessDeniedException (403) al invocar el dashboard")
    void user_denied() {
        assertThatThrownBy(() -> controller.ocupacion(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("un ADMIN atraviesa el guard de seguridad")
    void admin_allowed() {
        assertThatCode(() -> controller.ocupacion(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31)))
                .doesNotThrowAnyException();
    }
}
