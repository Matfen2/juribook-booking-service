package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publication réelle des événements slot.released sur Kafka.
 *
 * Même conditionnement que KafkaBookingEventPublisher : actif seulement
 * si un KafkaTemplate existe en contexte (Kafka non exclu). Voir
 * NoOpSlotEventPublisher pour le repli en dev local.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class KafkaSlotEventPublisher implements SlotEventPublisher {

    private static final String TOPIC = "slot-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishSlotReleased(Long lawyerId, Long slotId) {
        SlotReleasedEvent event = new SlotReleasedEvent(
                "slot.released", lawyerId, slotId, LocalDateTime.now());

        try {
            String payload = objectMapper.writeValueAsString(event);
            // Clé = slotId : garde l'ordre si jamais plusieurs événements
            // successifs concernaient le même créneau.
            kafkaTemplate.send(TOPIC, slotId.toString(), payload);
            log.info("Événement Kafka publié : type=slot.released, lawyerId={}, slotId={}, topic={}",
                    lawyerId, slotId, TOPIC);
        } catch (JsonProcessingException e) {
            log.error("Échec de sérialisation de l'événement slot.released pour slotId={}",
                    slotId, e);
        }
    }
}