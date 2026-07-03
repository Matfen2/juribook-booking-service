package juribook.booking_service.event;

import java.time.LocalDateTime;

/**
 * Miroir côté consumer du payload publié par le lawyer-service sur
 * lawyer-events (cf. juribook.lawyer_service.event.LawyerEvent).
 */
public record LawyerEvent(
    String eventType,
    Long lawyerId,
    Boolean available,
    LocalDateTime occurredAt
) {
}