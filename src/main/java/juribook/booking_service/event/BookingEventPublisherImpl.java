package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.entity.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publication des événements Booking sur Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisherImpl implements BookingEventPublisher {

    private static final String TOPIC = "booking-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void publishBookingCreated(Booking booking) {
        publish("booking.created", booking);
    }

    @Override
    public void publishBookingConfirmed(Booking booking) {
        publish("booking.confirmed", booking);
    }

    @Override
    public void publishBookingCancelled(Booking booking) {
        publish("booking.cancelled", booking);
    }

    @Override
    public void publishBookingReminder(Booking booking) {
        publish("booking.reminder", booking);
    }

    private void publish(String eventType, Booking booking) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            log.debug("Kafka désactivé - événement {} non publié pour bookingId={}",
                    eventType, booking.getId());
            return;
        }

        BookingEvent event = new BookingEvent(
                eventType,
                booking.getId(),
                booking.getClientId(),
                booking.getLawyerId(),
                booking.getTimeSlotId(),
                booking.getStatus().name(),
                booking.getReason(),
                LocalDateTime.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, booking.getId().toString(), payload);
            log.info("Événement Kafka publié : type={}, bookingId={}, topic={}",
                    eventType, booking.getId(), TOPIC);
        } catch (JsonProcessingException e) {
            log.error("Échec de sérialisation de l'événement {} pour bookingId={}",
                    eventType, booking.getId(), e);
        }
    }
}