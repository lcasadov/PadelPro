package com.padelpro.bloqueos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Blocked court time-slot (change bloqueos-pista-eventos, D1). Maps table {@code bloqueo_pista}
 * (V19). One row per blocked 60-min slot of a date; {@code hora} is the slot start (minute 0),
 * aligned with {@code SLOT_MINUTES = 60}. The pair {@code (fecha, hora)} is unique so re-blocking an
 * already-blocked slot is idempotent.
 *
 * <p>Persisted via the {@code bloqueos} capability; the availability calculation reads only the set
 * of blocked hours per date through {@code BloqueoQueryPort}, never this entity directly.
 */
@Entity
@Table(name = "bloqueo_pista")
public class BloqueoPista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BloqueoPista() {
        // JPA requires a no-arg constructor
    }

    public BloqueoPista(LocalDate fecha, LocalTime hora, String motivo, Long createdByUserId) {
        this.fecha = fecha;
        this.hora = hora;
        this.motivo = motivo;
        this.createdByUserId = createdByUserId;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getMotivo() {
        return motivo;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
