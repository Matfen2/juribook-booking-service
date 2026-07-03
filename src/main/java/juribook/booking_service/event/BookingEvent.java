package juribook.booking_service.event;

import java.time.LocalDateTime;

/**
 * Payload sérialisé en JSON et publié sur le topic Kafka booking-events.
 *
 * eventType vaut "booking.created", "booking.confirmed" ou
 * "booking.cancelled" (le refus d'une demande PENDING par l'avocat
 * publie aussi "booking.cancelled", il n'y a pas de statut BookingStatus
 * REJECTED distinct, cf. Booking.java).
 */
public record BookingEvent(
    String eventType,
    Long bookingId,
    Long clientId,
    Long lawyerId,
    Long timeSlotId,
    String status,
    String reason,
    LocalDateTime occurredAt
) {
}