package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.entity.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publication réelle des événements Booking sur Kafka.
 *
 * N'est instancié que si un bean KafkaTemplate existe en contexte, c'est-
 * à-dire seulement quand Kafka est activé (spring.autoconfigure.exclude
 * ne l'exclut pas, typiquement en production, cf. README). En dev local
 * sans broker, c'est NoOpBookingEventPublisher qui prend le relais.
 *
 * ⚠️ Simplification assumée pour ce sprint : la publication se fait de
 * façon synchrone, à l'intérieur de la même transaction @Transactional
 * que l'écriture en base (pas de pattern Outbox). En cas d'échec Kafka,
 * l'erreur est logguée mais n'annule pas la transaction métier — on
 * privilégie la disponibilité du service de réservation à la garantie de
 * livraison de l'événement. À revoir si un besoin de fiabilité stricte
 * apparaît (Sprint 5.1 : configuration complète des topics).
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class KafkaBookingEventPublisher implements BookingEventPublisher {

    private static final String TOPIC = "booking-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
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

    private void publish(String eventType, Booking booking) {
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
            // Clé = bookingId : garantit que tous les événements d'une même
            // réservation atterrissent dans la même partition, donc dans
            // l'ordre pour un consumer donné.
            kafkaTemplate.send(TOPIC, booking.getId().toString(), payload);
            log.info("Événement Kafka publié : type={}, bookingId={}, topic={}",
                    eventType, booking.getId(), TOPIC);
        } catch (JsonProcessingException e) {
            // La sérialisation d'un DTO aussi simple ne devrait jamais
            // échouer ; on logue plutôt que de faire échouer la
            // transaction métier pour un problème de publication.
            log.error("Échec de sérialisation de l'événement {} pour bookingId={}",
                    eventType, booking.getId(), e);
        }
    }
}