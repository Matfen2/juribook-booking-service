package juribook.booking_service.event;

import juribook.booking_service.entity.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Implémentation de repli utilisée quand Kafka est désactivé (dev local,
 * cf. spring.autoconfigure.exclude dans application.yaml, pas de broker
 * disponible). Logue l'événement à la place de le publier, pour ne pas
 * bloquer le développement local.
 */
@Component
@ConditionalOnMissingBean(KafkaTemplate.class)
@Slf4j
public class NoOpBookingEventPublisher implements BookingEventPublisher {

    @Override
    public void publishBookingCreated(Booking booking) {
        logSkipped("booking.created", booking);
    }

    @Override
    public void publishBookingConfirmed(Booking booking) {
        logSkipped("booking.confirmed", booking);
    }

    @Override
    public void publishBookingCancelled(Booking booking) {
        logSkipped("booking.cancelled", booking);
    }

    private void logSkipped(String eventType, Booking booking) {
        log.debug("Kafka désactivé - événement {} non publié pour bookingId={}",
                eventType, booking.getId());
    }
}