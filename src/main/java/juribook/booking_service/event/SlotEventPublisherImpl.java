package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publication des événements slot.released sur Kafka — implémentation
 * unique, même correction que BookingEventPublisherImpl (Sprint 5.2) :
 * décision à l'exécution via ObjectProvider<KafkaTemplate>, plus à
 * l'enregistrement du bean via @ConditionalOnBean (cassé par l'ordre
 * de scan des @Component vs autoconfiguration Spring Boot).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlotEventPublisherImpl implements SlotEventPublisher {

    private static final String TOPIC = "slot-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void publishSlotReleased(Long lawyerId, Long slotId) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            log.debug("Kafka désactivé - événement slot.released non publié pour lawyerId={}, slotId={}",
                    lawyerId, slotId);
            return;
        }

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