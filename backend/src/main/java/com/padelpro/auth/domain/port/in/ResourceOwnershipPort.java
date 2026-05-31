package com.padelpro.auth.domain.port.in;

/**
 * Outbound port for resource ownership verification.
 *
 * <p>Allows application services to check whether a given user owns or participates
 * in a resource without coupling the domain layer to the specific bounded context
 * (reservas, pagos-redsys) that holds the data.
 *
 * <p>Implementations are provided by the {@code reservas} and {@code pagos-redsys}
 * bounded contexts in future changes. Application services MUST use this port;
 * infrastructure.web controllers MUST NOT call it directly (see ArchUnit rules).
 */
public interface ResourceOwnershipPort {

    /**
     * Returns {@code true} if the given user is the owner of the specified resource.
     *
     * @param userId       the id of the user to check
     * @param resourceType the type of resource (e.g. "reserva", "pago")
     * @param resourceId   the string identifier of the resource
     * @return {@code true} if userId is the owner
     */
    boolean isOwner(Long userId, String resourceType, String resourceId);

    /**
     * Returns {@code true} if the given user is a participant in the given reservation.
     *
     * @param userId        the id of the user to check
     * @param reservationId the identifier of the reservation
     * @return {@code true} if userId is a participant
     */
    boolean isParticipant(Long userId, String reservationId);
}
