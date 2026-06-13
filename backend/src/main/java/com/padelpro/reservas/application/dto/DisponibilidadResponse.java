package com.padelpro.reservas.application.dto;

import java.util.List;

/**
 * Availability of the (single) court for a specific date (OpenAPI schema {@code DisponibilidadResponse}).
 *
 * <p>When the court is in MANTENIMIENTO (RN-RES-04) {@code tramosDisponibles} is an empty list and
 * no field reveals the reason.
 *
 * @param fecha             the queried date formatted as {@code YYYY-MM-DD}
 * @param tramosDisponibles slots that have at least one free seat (full slots are omitted)
 */
public record DisponibilidadResponse(String fecha, List<TramoDisponible> tramosDisponibles) {
}
