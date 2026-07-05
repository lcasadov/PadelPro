package com.padelpro.reservas.infrastructure.web;

import com.padelpro.reservas.application.dto.CrearReservaRequest;
import com.padelpro.reservas.application.dto.ReservaResponse;
import com.padelpro.reservas.application.dto.UnirseResponse;
import com.padelpro.reservas.application.service.AbandonarReservaService;
import com.padelpro.reservas.application.service.CancelarReservaService;
import com.padelpro.reservas.application.service.CrearReservaService;
import com.padelpro.reservas.application.service.ReservaQueryService;
import com.padelpro.reservas.application.service.UnirseReservaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user-facing reservations (capability reservas, US-007 / #14).
 *
 * <ul>
 *   <li>{@code POST   /api/reservas}        — create (201, idempotent via {@code Idempotency-Key})</li>
 *   <li>{@code GET    /api/reservas}        — list own (owner or participant, RN-AUTH-01)</li>
 *   <li>{@code GET    /api/reservas/{id}}   — detail (403 not 404 for non-owner USER, BOLA)</li>
 *   <li>{@code DELETE /api/reservas/{id}}   — cancel (owner or ADMIN, RN-AUTH-02)</li>
 *   <li>{@code POST   /api/reservas/{id}/unirse}       — join a match (capability partidas, D2)</li>
 *   <li>{@code DELETE /api/reservas/{id}/participacion} — leave a match (capability partidas, D3)</li>
 * </ul>
 *
 * <p>The JWT subject is the user id (Long as String); the role authority is {@code ROLE_USER}/
 * {@code ROLE_ADMIN} (see {@code JwtAuthFilter}). Anonymous requests get 401 via the filter chain.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final CrearReservaService crearReservaService;
    private final ReservaQueryService reservaQueryService;
    private final CancelarReservaService cancelarReservaService;
    private final UnirseReservaService unirseReservaService;
    private final AbandonarReservaService abandonarReservaService;

    public ReservaController(CrearReservaService crearReservaService,
                            ReservaQueryService reservaQueryService,
                            CancelarReservaService cancelarReservaService,
                            UnirseReservaService unirseReservaService,
                            AbandonarReservaService abandonarReservaService) {
        this.crearReservaService = crearReservaService;
        this.reservaQueryService = reservaQueryService;
        this.cancelarReservaService = cancelarReservaService;
        this.unirseReservaService = unirseReservaService;
        this.abandonarReservaService = abandonarReservaService;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @RequestBody CrearReservaRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        ReservaResponse response = crearReservaService.crear(currentUserId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listar() {
        return ResponseEntity.ok(reservaQueryService.listForUser(currentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> detalle(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(reservaQueryService.getForUser(id, currentUserId(), isAdmin()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable("id") UUID id) {
        cancelarReservaService.cancelar(id, currentUserId(), isAdmin());
        return ResponseEntity.noContent().build();
    }

    /** Join an open match (capability partidas, D2). No body; the user comes from the JWT. */
    @PostMapping("/{id}/unirse")
    public ResponseEntity<UnirseResponse> unirse(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(unirseReservaService.unirse(id, currentUserId()));
    }

    /** Leave a match a non-owner joined (capability partidas, D3). */
    @DeleteMapping("/{id}/participacion")
    public ResponseEntity<Void> abandonar(@PathVariable("id") UUID id) {
        abandonarReservaService.abandonar(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
