package com.padelpro.reservas.infrastructure.web;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.reservas.application.dto.PartidaAbiertaResponse;
import com.padelpro.reservas.application.service.PartidasQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * REST controller for open matches — reservations with free seats a user can join (capability
 * partidas, D1).
 *
 * <p>Endpoint: {@code GET /api/partidas?fecha=YYYY-MM-DD}. Requires an authenticated USER or ADMIN;
 * anonymous/expired tokens get 401 via the security filter chain ({@code anyRequest().authenticated()}).
 *
 * <p>{@code fecha} is mandatory ISO {@code YYYY-MM-DD}; a missing/malformed value yields 400
 * {@code VALIDATION_ERROR} through {@code GlobalExceptionHandler}, mirroring the availability endpoint.
 */
@RestController
@RequestMapping("/api/partidas")
public class PartidasController {

    /** Strict ISO date parser: rejects out-of-range values (e.g. 2025-13-40) and non-ISO formats. */
    private static final DateTimeFormatter FECHA_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final PartidasQueryService partidasQueryService;

    public PartidasController(PartidasQueryService partidasQueryService) {
        this.partidasQueryService = partidasQueryService;
    }

    @GetMapping
    public ResponseEntity<List<PartidaAbiertaResponse>> listar(
            @RequestParam(name = "fecha", required = false) String fecha) {
        LocalDate parsed = parseFecha(fecha);
        return ResponseEntity.ok(partidasQueryService.listOpenByDate(parsed));
    }

    private LocalDate parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw new ValidationException("El parámetro 'fecha' es obligatorio (formato YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(fecha.trim(), FECHA_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("El parámetro 'fecha' debe tener el formato YYYY-MM-DD");
        }
    }
}
