package com.padelpro.administracion.infrastructure.web;

import com.padelpro.administracion.application.dto.IngresosResponse;
import com.padelpro.administracion.application.dto.OcupacionResponse;
import com.padelpro.administracion.application.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * ADMIN dashboard endpoints (capability administracion-club).
 *
 * <ul>
 *   <li>{@code GET /api/admin/dashboard/ocupacion}  — court occupancy % for a date range (RN-ADM-02)</li>
 *   <li>{@code GET /api/admin/dashboard/ingresos}   — income with per-method breakdown (RN-ADM-03)</li>
 *   <li>{@code GET /api/admin/dashboard/exportar}   — CSV usage report, aggregates only (RN-ADM-04, RN-RGPD-04)</li>
 * </ul>
 *
 * <p>The {@code /api/admin/**} prefix is already restricted to {@code ROLE_ADMIN} by the security
 * filter chain (RN-ADM-01); {@link PreAuthorize} is added defensively (USER → 403).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/ocupacion")
    public ResponseEntity<OcupacionResponse> ocupacion(
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(dashboardService.ocupacion(fechaInicio, fechaFin));
    }

    @GetMapping("/ingresos")
    public ResponseEntity<IngresosResponse> ingresos(
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        return ResponseEntity.ok(dashboardService.ingresos(fechaInicio, fechaFin));
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        byte[] body = dashboardService.exportarCsv(fechaInicio, fechaFin).getBytes(StandardCharsets.UTF_8);
        String filename = "informe-uso-" + fechaInicio + "_" + fechaFin + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(body.length)
                .body(body);
    }
}
