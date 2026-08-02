package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consomme document-events : booking-service consomme ici
 * son PROPRE topic, sur lequel il publie aussi (document.uploaded,
 * document.ready). Pattern volontaire : le thread HTTP répond vite
 * (202, cf. BookingController), le traitement lourd (scan, déplacement
 * vers stockage permanent) est déporté sur ce consumer, qui tourne sur
 * un thread séparé, découplage réel sans avoir besoin d'un
 * microservice "document-processor" dédié.
 *
 * ⚠️ Ignore explicitement ses propres événements de sortie
 * (document.ready), sans ce filtre, ce même consumer les recevrait
 * aussi (topic unique) et tenterait de retraiter un document déjà prêt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentEventConsumer {

    private final ObjectMapper objectMapper;
    private final DocumentProcessingService documentProcessingService;

    @KafkaListener(topics = "document-events", groupId = "${spring.kafka.consumer.group-id}")
    public void onDocumentEvent(String payload) {
        DocumentEvent event;
        try {
            event = objectMapper.readValue(payload, DocumentEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Impossible de désérialiser un message du topic document-events : {}", payload, e);
            return;
        }

        if (!"document.uploaded".equals(event.eventType())) {
            log.debug("Événement document-events ignoré (eventType={})", event.eventType());
            return;
        }

        documentProcessingService.processDocument(event.documentId());
    }
}