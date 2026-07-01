package juribook.booking_service.event;

import java.time.LocalDateTime;

/**
 * Payload sérialisé en JSON et publié sur le topic Kafka slot-events
 * quand un créneau redevient AVAILABLE suite à une annulation (reject ou
 * cancel d'une réservation).
 *
 * Volontairement minimal (lawyerId + slotId, cf. cahier des charges),
 * un futur consumer liste d'attente (Sprint 4.9) rappellera le
 * booking-service via GET /api/lawyers/{lawyerId}/slots pour les détails
 * du créneau plutôt que de les dupliquer ici.
 */
public record SlotReleasedEvent(
    String eventType,
    Long lawyerId,
    Long slotId,
    LocalDateTime occurredAt
) {
}