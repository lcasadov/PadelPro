package com.padelpro.reservas.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency record for {@code POST /api/reservas} (design D2, table {@code idempotency_keys}).
 *
 * <p>Scope is per-user: {@code UNIQUE(user_id, idem_key)}. A second create request carrying the
 * same {@code Idempotency-Key} for the same user resolves to the {@code reservationId} stored here
 * instead of inserting a duplicate reservation.
 *
 * <p>The column is named {@code idem_key} (not {@code key}) to avoid the SQL reserved word.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false)
    private String idemKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idemKey, Long userId, UUID reservationId) {
        this.idemKey = idemKey;
        this.userId = userId;
        this.reservationId = reservationId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null) return false;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKey that = (IdempotencyKey) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
