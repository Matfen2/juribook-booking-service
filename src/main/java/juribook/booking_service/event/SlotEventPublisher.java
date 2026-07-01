package juribook.booking_service.event;

/**
 * Abstraction de publication des événements de libération de créneau,
 * sur le topic Kafka slot-events.
 *
 * Même principe que BookingEventPublisher : deux implémentations
 * sélectionnées automatiquement selon la présence d'un KafkaTemplate en
 * contexte (Kafka activé ou non).
 */
public interface SlotEventPublisher {

    void publishSlotReleased(Long lawyerId, Long slotId);
}