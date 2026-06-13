package com.padelpro.reservas.domain.model;

/**
 * Channel through which a reservation (or participant) was created
 * (data-model §3.3 / §3.4, enum {@code reservation_channel}).
 */
public enum ReservationChannel {
    WEB,
    TELEGRAM
}
