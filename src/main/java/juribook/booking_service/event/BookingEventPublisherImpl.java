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
 * Publication des événements Booking sur Kafka — implémentation unique,
 * qui décide À L'EXÉCUTION (pas à l'enregistrement du bean) si Kafka est
 * disponible.
 *
 * Historique du bug corrigé ici (Sprint 5.2) : l'ancienne approche à
 * deux classes (KafkaBookingEventPublisher avec @ConditionalOnBean(KafkaTemplate.class),
 * NoOpBookingEventPublisher avec @ConditionalOnMissingBean) semblait
 * raisonnable mais était cassée par un piège d'ordre classique de Spring
 * Boot : ces deux classes sont de simples @Component, scannées AVANT que
 * la plupart des autoconfigurations (dont KafkaAutoConfiguration) n'aient
 * tourné. Résultat : au moment où @ConditionalOnBean(KafkaTemplate.class)
 * était évalué, le KafkaTemplate n'existait pas encore dans le contexte
 * → la condition échouait systématiquement → NoOp gagnait toujours, même
 * quand Kafka était parfaitement configuré et actif. Le KafkaTemplate
 * finissait par être créé (par l'autoconfiguration, plus tard), mais
 * plus rien ne l'utilisait.
 *
 * ObjectProvider<KafkaTemplate> contourne ce piège : sa résolution est
 * paresseuse, appelée à chaque publication plutôt qu'une seule fois à
 * l'enregistrement du bean, donc elle voit l'état final et complet du
 * contexte Spring, une fois toutes les autoconfigurations terminées.
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

    private void publish(String eventType, Booking booking) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            // Kafka désactivé (dev local sans broker, cf. application.yaml)
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