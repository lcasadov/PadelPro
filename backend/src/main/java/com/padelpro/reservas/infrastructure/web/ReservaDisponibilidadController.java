package com.padelpro.reservas.infrastructure.web;

import com.padelpro.auth.domain.exception.ValidationException;
import com.padelpro.reservas.application.dto.DisponibilidadResponse;
import com.padelpro.reservas.application.service.DisponibilidadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * REST controller for court availability (capability disponibilidad-pistas, US-006 / #13).
 *
 * <p>Endpoint: {@code GET /api/reservas/disponibles?fecha=YYYY-MM-DD} (OpenAPI operationId
 * {@code getDisponibilidad}). Requires an authenticated USER or ADMIN; anonymous/expired tokens get
 * 401 via the security filter chain ({@code anyRequest().authenticated()}).
 *
 * <p>The {@code fecha} parameter is mandatory and must be ISO {@code YYYY-MM-DD}; a missing or
 * malformed value yields 400 {@code VALIDATION_ERROR} through {@code GlobalExceptionHandler}
 * (which maps {@link ValidationException}). Declaring {@code fecha} as required (and not binding to
 * a {@code LocalDate} directly) keeps full control over the error code instead of producing a
 * generic Spring type-mismatch / missing-parameter response.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaDisponibilidadController {

    /** Strict ISO date parser: rejects out-of-range values (e.g. 2025-13-40) and non-ISO formats. */
    private static final DateTimeFormatter FECHA_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final DisponibilidadService disponibilidadService;

    public ReservaDisponibilidadController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
    }

    @GetMapping("/disponibles")
    public ResponseEntity<DisponibilidadResponse> getDisponibilidad(
            @RequestParam(name = "fecha", required = false) String fecha) {
        LocalDate parsed = parseFecha(fecha);
        return ResponseEntity.ok(disponibilidadService.getDisponibilidad(parsed));
    }

    private LocalDate parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw new ValidationException("El parámetro 'fecha' es obligatorio (formato YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(fecha.trim(), FECHA_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "El parámetro 'fecha' debe tener el formato YYYY-MM-DD");
        }
    }
}
