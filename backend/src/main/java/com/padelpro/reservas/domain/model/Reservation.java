package com.padelpro.reservas.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reservation aggregate root (maps table {@code reservations}, data-model §3.3).
 *
 * <p>In the capability disponibilidad-pistas this entity is used read-only: the availability
 * service queries active reservations for a given date together with their participants in
 * order to compute free slots. Write operations (create/confirm/cancel) belong to Wave 3.
 *
 * <p>The Postgres enum columns ({@code reservation_status}, {@code reservation_channel}) are
 * mapped as {@code EnumType.STRING}; Hibernate sends the text value which Postgres casts to the
 * enum type. Under the H2 (PostgreSQL mode) test profile the columns are plain varchars, so the
 * same mapping works for both engines.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    // The Flyway-created columns are native PostgreSQL enum types (reservation_status,
    // reservation_channel). PostgreSQL does NOT implicitly cast a bound varchar parameter to a
    // native enum, so a plain EnumType.STRING mapping fails on INSERT/UPDATE with
    // "column is of type reservation_channel but expression is of type character varying" (#160-adj).
    // @JdbcTypeCode(SqlTypes.NAMED_ENUM) makes Hibernate bind the value as the named DB enum type,
    // which works against native PG enums and is tolerated by H2 (PostgreSQL mode) in tests.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "reservation_status")
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "reservation_channel")
    private ReservationChannel channel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "telegram_message_id")
    private String telegramMessageId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    protected Reservation() {
    }

    /**
     * Factory for the write path (capability reservas, US-007). Creates a brand-new reservation in
     * {@code PENDING_CONFIRMATION} with its {@code end_time} derived from {@code start + duration} so
     * that the DB CHECK {@code chk_res_end_time} and the gist exclusion constraint match exactly.
     */
    public static Reservation create(Long ownerId, LocalDate reservationDate, LocalTime startTime,
                                     Integer durationMinutes, ReservationChannel channel, String notes) {
        Reservation r = new Reservation();
        r.ownerId = ownerId;
        r.reservationDate = reservationDate;
        r.startTime = startTime;
        r.durationMinutes = durationMinutes;
        r.endTime = startTime.plusMinutes(durationMinutes);
        r.status = ReservationStatus.PENDING_CONFIRMATION;
        r.channel = channel;
        r.notes = notes;
        return r;
    }

    /** Apply a (pre-validated) status transition. Validity is enforced by the state machine. */
    public void changeStatus(ReservationStatus newStatus) {
        this.status = newStatus;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    /** Attach a participant to this reservation, keeping both sides of the association in sync. */
    public void addParticipant(Participant participant) {
        participant.attachTo(this);
        this.participants.add(participant);
    }

    /**
     * Detach a participant from this reservation (capability partidas, D3 — abandon). With
     * {@code orphanRemoval=true} the removed row is deleted on flush, freeing its seat.
     */
    public void removeParticipant(Participant participant) {
        this.participants.remove(participant);
    }

    /** Next free {@code slot_position} for a new participant: {@code max(slot) + 1} (D2). */
    public int nextSlotPosition() {
        return participants.stream()
                .mapToInt(Participant::getSlotPosition)
                .max()
                .orElse(0) + 1;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Whether this reservation occupies its time slot for availability purposes (RN-RES-01):
     * {@code PENDING_CONFIRMATION} and {@code CONFIRMED} occupy; {@code CANCELLED}/{@code COMPLETED} do not.
     */
    public boolean isActiveOccupant() {
        return status == ReservationStatus.PENDING_CONFIRMATION
                || status == ReservationStatus.CONFIRMED;
    }

    /**
     * Whether this reservation overlaps the half-open interval {@code [slotStart, slotEnd)} on
     * its own date. Overlap uses the same {@code '[)'} semantics as the DB exclusion constraint.
     */
    public boolean overlaps(LocalTime slotStart, LocalTime slotEnd) {
        return startTime.isBefore(slotEnd) && endTime.isAfter(slotStart);
    }

    public UUID getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public ReservationChannel getChannel() {
        return channel;
    }

    public String getNotes() {
        return notes;
    }

    public String getTelegramMessageId() {
        return telegramMessageId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (id == null) return false;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Test/factory builder. Production write paths (Wave 3) will replace this. */
    public static class Builder {
        private final Reservation r = new Reservation();

        public Builder id(UUID id) {
            r.id = id;
            return this;
        }

        public Builder ownerId(Long ownerId) {
            r.ownerId = ownerId;
            return this;
        }

        public Builder reservationDate(LocalDate date) {
            r.reservationDate = date;
            return this;
        }

        public Builder startTime(LocalTime startTime) {
            r.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalTime endTime) {
            r.endTime = endTime;
            return this;
        }

        public Builder durationMinutes(Integer durationMinutes) {
            r.durationMinutes = durationMinutes;
            return this;
        }

        public Builder status(ReservationStatus status) {
            r.status = status;
            return this;
        }

        public Builder channel(ReservationChannel channel) {
            r.channel = channel;
            return this;
        }

        public Builder participants(List<Participant> participants) {
            r.participants = participants;
            return this;
        }

        public Reservation build() {
            return r;
        }
    }
}
