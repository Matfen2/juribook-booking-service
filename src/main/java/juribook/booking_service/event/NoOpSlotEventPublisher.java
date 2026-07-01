package juribook.booking_service.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Implémentation de repli pour slot-events, utilisée quand Kafka est
 * désactivé (dev local, pas de broker disponible). Logue l'événement
 * plutôt que de le publier.
 */
@Component
@ConditionalOnMissingBean(KafkaTemplate.class)
@Slf4j
public class NoOpSlotEventPublisher implements SlotEventPublisher {

    @Override
    public void publishSlotReleased(Long lawyerId, Long slotId) {
        log.debug("Kafka désactivé — événement slot.released non publié pour lawyerId={}, slotId={}",
                lawyerId, slotId);
    }
}