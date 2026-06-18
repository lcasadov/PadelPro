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

    /**
     * Owner participant: slot 1, {@code is_owner=true}, registered user (capability reservas, US-007).
     */
    public static Participant owner(Long userId, ReservationChannel joinedVia) {
        Participant p = new Participant();
        p.userId = userId;
        p.slotPosition = 1;
        p.owner = true;
        p.joinedVia = joinedVia;
        return p;
    }

    /**
     * External (non-registered) guest at the given slot. The DB XOR constraint requires
     * {@code external_name != null} and {@code user_id == null}.
     */
    public static Participant external(String externalName, String externalPhone,
                                       int slotPosition, ReservationChannel joinedVia) {
        Participant p = new Participant();
        p.externalName = externalName;
        p.externalPhone = externalPhone;
        p.slotPosition = slotPosition;
        p.owner = false;
        p.joinedVia = joinedVia;
        return p;
    }

    /** Registered additional participant (not the owner) at the given slot. */
    public static Participant registered(Long userId, int slotPosition, ReservationChannel joinedVia) {
        Participant p = new Participant();
        p.userId = userId;
        p.slotPosition = slotPosition;
        p.owner = false;
        p.joinedVia = joinedVia;
        return p;
    }

    /** Package-internal wiring used by {@link Reservation#addParticipant(Participant)}. */
    void attachTo(Reservation reservation) {
        this.reservation = reservation;
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
