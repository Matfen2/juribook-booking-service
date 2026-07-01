package juribook.booking_service.event;

import juribook.booking_service.entity.Booking;

/**
 * Abstraction de publication des événements liés au cycle de vie d'un
 * Booking, sur le topic Kafka booking-events.
 *
 * Deux implémentations, sélectionnées automatiquement selon la présence
 * ou non d'un KafkaTemplate en contexte (donc selon que Kafka est activé
 * ou non, cf. spring.autoconfigure.exclude dans application.yaml) :
 *   - KafkaBookingEventPublisher : publication réelle (prod / Kafka actif)
 *   - NoOpBookingEventPublisher  : simple log (dev local sans broker)
 *
 * Ainsi BookingService n'a jamais à savoir si Kafka est disponible ou non.
 */
public interface BookingEventPublisher {

    void publishBookingCreated(Booking booking);

    void publishBookingConfirmed(Booking booking);

    void publishBookingCancelled(Booking booking);
}