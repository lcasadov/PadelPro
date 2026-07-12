package com.padelpro.administracion.infrastructure.web;

import com.padelpro.administracion.application.dto.IngresosResponse;
import com.padelpro.administracion.application.dto.OcupacionResponse;
import com.padelpro.administracion.application.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AdminDashboardController} (capability administracion-club). Standalone
 * MockMvc verifies request mapping, query-param binding, response bodies and the CSV download
 * headers. Method-level {@code @PreAuthorize} security is verified separately in
 * {@code AdminDashboardControllerSecurityTest}.
 */
@DisplayName("AdminDashboardController — /api/admin/dashboard")
class AdminDashboardControllerTest {

    private final DashboardService dashboardService = Mockito.mock(DashboardService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminDashboardController(dashboardService)).build();
    }

    @Test
    @DisplayName("GET /ocupacion devuelve el DTO de ocupación")
    void ocupacion_ok() throws Exception {
        LocalDate inicio = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        when(dashboardService.ocupacion(eq(inicio), eq(fin)))
                .thenReturn(new OcupacionResponse(inicio, fin, 3, 15, new BigDecimal("20.0")));

        mockMvc.perform(get("/api/admin/dashboard/ocupacion")
                        .param("fechaInicio", "2025-05-01")
                        .param("fechaFin", "2025-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotsReservados", is(3)))
                .andExpect(jsonPath("$.slotsDisponibles", is(15)))
                .andExpect(jsonPath("$.ocupacionPct", is(20.0)));
    }

    @Test
    @DisplayName("GET /ingresos devuelve total y desglose por método")
    void ingresos_ok() throws Exception {
        LocalDate inicio = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
        porMetodo.put("REDSYS", new BigDecimal("45.00"));
        porMetodo.put("CASH", new BigDecimal("30.00"));
        when(dashboardService.ingresos(eq(inicio), eq(fin)))
                .thenReturn(new IngresosResponse(inicio, fin, new BigDecimal("75.00"), porMetodo));

        mockMvc.perform(get("/api/admin/dashboard/ingresos")
                        .param("fechaInicio", "2025-05-01")
                        .param("fechaFin", "2025-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(75.00)))
                .andExpect(jsonPath("$.porMetodo.REDSYS", is(45.00)))
                .andExpect(jsonPath("$.porMetodo.CASH", is(30.00)));
    }

    @Test
    @DisplayName("GET /exportar devuelve text/csv con Content-Disposition attachment")
    void exportar_devuelveCsvDescargable() throws Exception {
        LocalDate inicio = LocalDate.of(2025, 5, 1);
        LocalDate fin = LocalDate.of(2025, 5, 31);
        String csv = "fecha,reservas,ocupacion_pct,ingresos_total,ingresos_redsys,ingresos_cash\r\n"
                + "2025-05-01,2,13.3,45.00,45.00,0.00\r\n";
        when(dashboardService.exportarCsv(eq(inicio), eq(fin))).thenReturn(csv);

        mockMvc.perform(get("/api/admin/dashboard/exportar")
                        .param("fechaInicio", "2025-05-01")
                        .param("fechaFin", "2025-05-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"informe-uso-2025-05-01_2025-05-31.csv\""))
                .andExpect(content().string(containsString("fecha,reservas,ocupacion_pct")))
                .andExpect(content().string(containsString("2025-05-01,2,13.3,45.00,45.00,0.00")));
    }
}
