package com.padelpro.reservas.application.dto;

/**
 * Request body for {@code PATCH /api/admin/reservas/{id}/estado} (capability reservas, US-007).
 */
public record CambiarEstadoRequest(String status) {
}
