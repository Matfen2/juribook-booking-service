package juribook.booking_service.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Payload publié sur booking-events.
 *
 * ⚠️ Reconstruit à partir du site d'appel dans BookingEventPublisherImpl
 * (record positionnel), si le fichier original avait d'autres champs ou
 * un ordre différent, ajuste en conséquence.
 *
 * slotDate/slotStartTime ajoutés pour la métrique "heures de
 * pointe" côté audit-service (heures des RENDEZ-VOUS, pas des créations
 * de réservation, occurredAt reste l'heure de publication de l'event).
 * Nullable si jamais le TimeSlot associé a disparu entre-temps (ne
 * devrait pas arriver en pratique, cf. BookingEventPublisherImpl).
 */
public record BookingEvent(
    String eventType,
    Long bookingId,
    Long clientId,
    Long lawyerId,
    Long timeSlotId,
    String status,
    String reason,
    LocalDateTime occurredAt,
    LocalDate slotDate,
    LocalTime slotStartTime
) {
}