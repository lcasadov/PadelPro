package com.padelpro.reservas.application.service;

import java.time.LocalDate;

/**
 * Reusable invalidation hook for the {@code available-slots} cache (D6, data-model §7.3).
 *
 * <p>This wave is read-only, so nothing invokes invalidation yet. Wave 3 (capability {@code reservas})
 * MUST call {@link #invalidate(LocalDate)} after creating or cancelling a reservation so that the
 * 30 s cache never serves a slot as free immediately after it was taken.
 */
public interface DisponibilidadCacheInvalidator {

    /**
     * Evict the cached availability for the given date.
     *
     * @param fecha the date whose cached availability must be discarded
     */
    void invalidate(LocalDate fecha);
}
