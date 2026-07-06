package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Publication des événements Booking sur Kafka.
 *
 * Enrichit désormais chaque événement avec la date/heure du
 * TimeSlot associé (slotDate/slotStartTime), en plus de occurredAt (heure
 * de publication). Nécessaire pour la métrique "heures de pointe"
 * côté audit-service, l'heure du RENDEZ-VOUS, pas celle de la réservation.
 * Lecture du TimeSlot ici (transactionnelle, données déjà en base au
 * moment de la publication), reste cohérent avec le principe "audit-
 * service ne lit que les events, jamais les tables transactionnelles" :
 * c'est booking-service, propriétaire de ses propres données, qui enrichit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisherImpl implements BookingEventPublisher {

    private static final String TOPIC = "booking-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private final TimeSlotRepository timeSlotRepository;

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

        LocalDate slotDate = null;
        LocalTime slotStartTime = null;

        TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId()).orElse(null);
        if (slot != null) {
            slotDate = slot.getDate();
            slotStartTime = slot.getStartTime();
        } else {
            // Ne devrait pas arriver (un booking référence toujours un
            // slot existant), mais on ne bloque jamais la publication
            // pour ça, juste privé de date/heure pour les stats.
            log.warn("TimeSlot introuvable pour l'enrichissement de l'événement {} : timeSlotId={}, bookingId={}",
                    eventType, booking.getTimeSlotId(), booking.getId());
        }

        BookingEvent event = new BookingEvent(
                eventType,
                booking.getId(),
                booking.getClientId(),
                booking.getLawyerId(),
                booking.getTimeSlotId(),
                booking.getStatus().name(),
                booking.getReason(),
                LocalDateTime.now(),
                slotDate,
                slotStartTime
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