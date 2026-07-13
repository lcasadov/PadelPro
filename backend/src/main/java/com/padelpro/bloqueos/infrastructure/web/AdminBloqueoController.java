package com.padelpro.bloqueos.infrastructure.web;

import com.padelpro.bloqueos.application.dto.BloqueoResponse;
import com.padelpro.bloqueos.application.dto.CrearBloqueoRequest;
import com.padelpro.bloqueos.application.service.BloqueoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * ADMIN court-block endpoints (change bloqueos-pista-eventos, D6).
 *
 * <ul>
 *   <li>{@code POST   /api/admin/bloqueos}          — block one or more slots of a date</li>
 *   <li>{@code GET    /api/admin/bloqueos?fecha=}   — list the blocks of a date</li>
 *   <li>{@code DELETE /api/admin/bloqueos/{id}}     — remove a block</li>
 * </ul>
 *
 * <p>{@code /api/admin/**} is already restricted to {@code ROLE_ADMIN} by the security filter chain;
 * {@link PreAuthorize} is added defensively (USER → 403). A create whose requested hours overlap an
 * active reservation returns 409 {@code CONFLICT} with the conflicting slots in {@code details}
 * (handled centrally by {@code GlobalExceptionHandler}).
 */
@RestController
@RequestMapping("/api/admin/bloqueos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBloqueoController {

    private final BloqueoService bloqueoService;

    public AdminBloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    @PostMapping
    public ResponseEntity<List<BloqueoResponse>> crear(@RequestBody CrearBloqueoRequest request) {
        List<BloqueoResponse> creados = bloqueoService.crear(
                request.fecha(), request.horas(), request.motivo(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(creados);
    }

    @GetMapping
    public ResponseEntity<List<BloqueoResponse>> listar(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(bloqueoService.listar(fecha));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") Long id) {
        bloqueoService.eliminar(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong((String) auth.getPrincipal());
    }
}
