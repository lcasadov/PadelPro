package com.padelpro.pagos.infrastructure.web;

import com.padelpro.pagos.application.dto.EfectivoPagoResponse;
import com.padelpro.pagos.application.dto.PagoHistorialResponse;
import com.padelpro.pagos.application.service.PagoQueryService;
import com.padelpro.pagos.application.service.RegistrarPagoEfectivoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN payment endpoints (pagos-redsys-online, group 5).
 *
 * <ul>
 *   <li>{@code POST /api/admin/pagos/{reservaId}/efectivo} — register a cash payment</li>
 *   <li>{@code GET  /api/admin/pagos}                      — every payment in the club</li>
 * </ul>
 *
 * <p>{@code /api/admin/**} is already restricted to {@code ROLE_ADMIN} by the security filter chain;
 * {@link PreAuthorize} is added defensively (USER → 403).
 */
@RestController
@RequestMapping("/api/admin/pagos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPagoController {

    private final RegistrarPagoEfectivoService registrarPagoEfectivoService;
    private final PagoQueryService pagoQueryService;

    public AdminPagoController(RegistrarPagoEfectivoService registrarPagoEfectivoService,
                              PagoQueryService pagoQueryService) {
        this.registrarPagoEfectivoService = registrarPagoEfectivoService;
        this.pagoQueryService = pagoQueryService;
    }

    @PostMapping("/{reservaId}/efectivo")
    public ResponseEntity<EfectivoPagoResponse> registrarEfectivo(@PathVariable("reservaId") UUID reservaId) {
        return ResponseEntity.ok(registrarPagoEfectivoService.registrar(reservaId, currentUserId()));
    }

    @GetMapping
    public ResponseEntity<List<PagoHistorialResponse>> listarTodos() {
        return ResponseEntity.ok(pagoQueryService.listAll());
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
