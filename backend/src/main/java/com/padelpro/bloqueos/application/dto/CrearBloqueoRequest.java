package com.padelpro.bloqueos.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Request body for {@code POST /api/admin/bloqueos} (change bloqueos-pista-eventos, D6):
 * {@code { "fecha": "2026-08-01", "horas": ["18:00","19:00"], "motivo": "Torneo" }}.
 *
 * <p>{@code fecha} and each {@code horas} entry are bound by Jackson from ISO date / {@code HH:mm}
 * strings. {@code motivo} is optional.
 *
 * @param fecha  the date to block
 * @param horas  slot-start hours to block (60-min granularity)
 * @param motivo optional human-readable reason (never revealed to players)
 */
public record CrearBloqueoRequest(LocalDate fecha, List<LocalTime> horas, String motivo) {
}
