package com.padelpro.reservas.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Participant of a reservation (maps table {@code participants}, data-model §3.4).
 *
 * <p>A participant is either a registered user ({@code userId != null}) or an external guest
 * ({@code externalName != null}); the DB enforces the XOR. For availability the only fact that
 * matters is the count of participants attached to active reservations overlapping a slot
 * (RN-RES-02: {@code plazasLibres = max_participants − COUNT(active participants)}).
 */
@Entity
@Table(name = "participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "external_name")
    private String externalName;

    @Column(name = "external_phone")
    private String externalPhone;

    @Column(name = "slot_position", nullable = false)
    private Integer slotPosition;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "joined_via", nullable = false)
    private ReservationChannel joinedVia;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    protected Participant() {
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public Long getUserId() {
        return userId;
    }

    public String getExternalName() {
        return externalName;
    }

    public String getExternalPhone() {
        return externalPhone;
    }

    public Integer getSlotPosition() {
        return slotPosition;
    }

    public boolean isOwner() {
        return owner;
    }

    public ReservationChannel getJoinedVia() {
        return joinedVia;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null) return false;
        if (o == null || getClass() != o.getClass()) return false;
        Participant that = (Participant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
