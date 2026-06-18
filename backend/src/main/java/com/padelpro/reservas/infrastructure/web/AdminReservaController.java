package com.padelpro.reservas.infrastructure.web;

import com.padelpro.reservas.application.dto.CambiarEstadoRequest;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.application.service.AdminReservaService;
import com.padelpro.reservas.application.service.ReservaQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for ADMIN reservation management (capability reservas, US-007 / #14).
 *
 * <ul>
 *   <li>{@code GET   /api/admin/reservas}            — list every reservation in the club</li>
 *   <li>{@code PATCH /api/admin/reservas/{id}/estado} — change state (bypass policy, D-RES-03)</li>
 * </ul>
 *
 * <p>The {@code /api/admin/**} prefix is already restricted to {@code ROLE_ADMIN} by the security
 * filter chain; {@link PreAuthorize} is added defensively (USER → 403).
 */
@RestController
@RequestMapping("/api/admin/reservas")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReservaController {

    private final ReservaQueryService reservaQueryService;
    private final AdminReservaService adminReservaService;

    public AdminReservaController(ReservaQueryService reservaQueryService,
                                 AdminReservaService adminReservaService) {
        this.reservaQueryService = reservaQueryService;
        this.adminReservaService = adminReservaService;
    }

    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listarTodas() {
        return ResponseEntity.ok(reservaQueryService.listAll());
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<ReservaResponse> cambiarEstado(
            @PathVariable("id") UUID id,
            @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(adminReservaService.cambiarEstado(id, request.status()));
    }
}
