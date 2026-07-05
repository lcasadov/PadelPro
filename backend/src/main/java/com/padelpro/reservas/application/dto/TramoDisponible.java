package com.padelpro.reservas.application.dto;

/**
 * A single available time slot with its free seats (OpenAPI schema {@code TramoDisponible}).
 *
 * @param horaInicio      slot start time formatted as {@code HH:mm} (e.g. {@code "09:00"})
 * @param duracionMinutos slot length in minutes (60 by default; see {@code DisponibilidadService})
 * @param plazasLibres    number of free seats in the slot; always {@code >= 1} (full slots are omitted)
 * @param creable         {@code true} iff the slot is completely empty ({@code plazasLibres == max_participants});
 *                        {@code false} when an incomplete reservation already occupies the slot (only joinable, not
 *                        creatable). Additive, backward-compatible flag (design D7).
 */
public record TramoDisponible(String horaInicio, int duracionMinutos, int plazasLibres, boolean creable) {
}
