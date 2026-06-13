package com.padelpro.reservas.domain.model;

import java.time.LocalTime;

/**
 * Read model describing how much of a time window an active reservation occupies on a given date.
 *
 * <p>Used by the availability calculation (RN-RES-02): for each requested slot the service sums
 * {@code participantCount} of the occupancies whose {@code [startTime, endTime)} interval overlaps
 * the slot, and derives {@code plazasLibres = maxParticipants − occupied}.
 *
 * @param startTime        reservation start (inclusive)
 * @param endTime          reservation end (exclusive)
 * @param participantCount number of participants attached to the reservation
 */
public record ReservationOccupancy(LocalTime startTime, LocalTime endTime, int participantCount) {

    /**
     * Whether this occupancy overlaps the half-open slot {@code [slotStart, slotEnd)}.
     * Uses the same {@code '[)'} semantics as the DB {@code excl_res_no_overlap} constraint.
     */
    public boolean overlaps(LocalTime slotStart, LocalTime slotEnd) {
        return startTime.isBefore(slotEnd) && endTime.isAfter(slotStart);
    }
}
