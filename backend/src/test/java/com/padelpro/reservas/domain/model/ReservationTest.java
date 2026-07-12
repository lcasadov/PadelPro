package com.padelpro.reservas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for the {@link Reservation} aggregate root (change
 * backend-branch-coverage, D1/D2). Exercises the lifecycle callbacks, the occupancy/overlap
 * predicates (RN-RES-01) and the identity contract ({@code equals}/{@code hashCode}).
 *
 * <p>Lives in the same package as {@link Reservation} so the JPA-protected {@code onCreate}/
 * {@code onUpdate} callbacks can be driven directly without a persistence context.
 */
@DisplayName("Unit — Reservation (domain)")
class ReservationTest {

    private static Reservation withId(UUID id) {
        return Reservation.builder()
                .id(id)
                .ownerId(1L)
                .reservationDate(LocalDate.of(2030, 1, 10))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0))
                .durationMinutes(60)
                .status(ReservationStatus.CONFIRMED)
                .channel(ReservationChannel.WEB)
                .build();
    }

    @Nested
    @DisplayName("create + lifecycle callbacks")
    class Lifecycle {

        @Test
        @DisplayName("create derives end_time and leaves id/timestamps unset until persist")
        void create_derives_end_time() {
            Reservation r = Reservation.create(1L, LocalDate.of(2030, 1, 10),
                    LocalTime.of(18, 0), 90, ReservationChannel.WEB, "note");

            assertThat(r.getEndTime()).isEqualTo(LocalTime.of(19, 30));
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING_CONFIRMATION);
            assertThat(r.getId()).isNull();
            assertThat(r.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("onCreate assigns id + timestamps only when unset (both branches)")
        void onCreate_populates_only_when_unset() {
            Reservation r = Reservation.create(1L, LocalDate.of(2030, 1, 10),
                    LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);

            // First call: id/createdAt/updatedAt are null → the true branch of every guard.
            r.onCreate();
            UUID assignedId = r.getId();
            var createdAt = r.getCreatedAt();
            assertThat(assignedId).isNotNull();
            assertThat(createdAt).isNotNull();
            assertThat(r.getUpdatedAt()).isNotNull();

            // Second call: everything already set → the false branch of every guard (no overwrite).
            r.onCreate();
            assertThat(r.getId()).isEqualTo(assignedId);
            assertThat(r.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("onUpdate refreshes updated_at")
        void onUpdate_refreshes_timestamp() {
            Reservation r = withId(UUID.randomUUID());
            r.onUpdate();
            assertThat(r.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("changeStatus + setCancellationReason mutate state")
        void changeStatus_and_reason() {
            Reservation r = withId(UUID.randomUUID());
            r.changeStatus(ReservationStatus.CANCELLED);
            r.setCancellationReason("admin");
            assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(r.getCancellationReason()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("isActiveOccupant (RN-RES-01)")
    class ActiveOccupant {

        @Test
        @DisplayName("PENDING_CONFIRMATION occupies (first condition true)")
        void pending_occupies() {
            Reservation r = Reservation.create(1L, LocalDate.of(2030, 1, 10),
                    LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);
            assertThat(r.isActiveOccupant()).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED occupies (second condition true)")
        void confirmed_occupies() {
            Reservation r = withId(UUID.randomUUID());
            r.changeStatus(ReservationStatus.CONFIRMED);
            assertThat(r.isActiveOccupant()).isTrue();
        }

        @Test
        @DisplayName("CANCELLED and COMPLETED do not occupy (both conditions false)")
        void terminal_does_not_occupy() {
            Reservation cancelled = withId(UUID.randomUUID());
            cancelled.changeStatus(ReservationStatus.CANCELLED);
            Reservation completed = withId(UUID.randomUUID());
            completed.changeStatus(ReservationStatus.COMPLETED);
            assertThat(cancelled.isActiveOccupant()).isFalse();
            assertThat(completed.isActiveOccupant()).isFalse();
        }
    }

    @Nested
    @DisplayName("overlaps (half-open [) semantics)")
    class Overlaps {

        private final Reservation r = // 18:00–19:00
                Reservation.create(1L, LocalDate.of(2030, 1, 10),
                        LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);

        @Test
        @DisplayName("overlapping interval → true (both conditions true)")
        void overlapping() {
            assertThat(r.overlaps(LocalTime.of(18, 30), LocalTime.of(19, 30))).isTrue();
        }

        @Test
        @DisplayName("interval entirely after → false (startTime not before slotEnd)")
        void entirely_after() {
            assertThat(r.overlaps(LocalTime.of(19, 0), LocalTime.of(20, 0))).isFalse();
        }

        @Test
        @DisplayName("interval entirely before → false (endTime not after slotStart)")
        void entirely_before() {
            assertThat(r.overlaps(LocalTime.of(16, 0), LocalTime.of(18, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("participants")
    class Participants {

        @Test
        @DisplayName("nextSlotPosition is 1 when empty, max+1 otherwise; removeParticipant frees a seat")
        void slot_positions() {
            Reservation r = Reservation.create(1L, LocalDate.of(2030, 1, 10),
                    LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);

            assertThat(r.nextSlotPosition()).isEqualTo(1); // orElse(0) + 1

            Participant owner = Participant.owner(1L, ReservationChannel.WEB);
            r.addParticipant(owner);
            assertThat(r.nextSlotPosition()).isEqualTo(2); // max(1) + 1
            assertThat(r.getParticipants()).hasSize(1);

            r.removeParticipant(owner);
            assertThat(r.getParticipants()).isEmpty();
        }
    }

    @Nested
    @DisplayName("equals / hashCode identity contract")
    class Identity {

        @Test
        @DisplayName("reflexive: an instance equals itself")
        void reflexive() {
            Reservation r = withId(UUID.randomUUID());
            assertThat(r.equals(r)).isTrue();
        }

        @Test
        @DisplayName("a transient (null id) reservation is never equal to another")
        void transient_not_equal() {
            Reservation transient1 = Reservation.create(1L, LocalDate.of(2030, 1, 10),
                    LocalTime.of(18, 0), 60, ReservationChannel.WEB, null);
            Reservation other = withId(UUID.randomUUID());
            assertThat(transient1.equals(other)).isFalse();
            // hashCode of a transient falls back to identity hash (id == null branch)
            assertThat(transient1.hashCode()).isEqualTo(System.identityHashCode(transient1));
        }

        @Test
        @DisplayName("persisted instance is not equal to null nor to a different type")
        void not_equal_null_or_other_type() {
            Reservation r = withId(UUID.randomUUID());
            assertThat(r.equals(null)).isFalse();
            assertThat(r.equals("not-a-reservation")).isFalse();
        }

        @Test
        @DisplayName("same id → equal + same hashCode; different id → not equal")
        void by_id() {
            UUID id = UUID.randomUUID();
            Reservation a = withId(id);
            Reservation b = withId(id);
            Reservation c = withId(UUID.randomUUID());

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }
}
