package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.entity.BookingDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentEventPublisherImpl implements DocumentEventPublisher {

    private static final String TOPIC = "document-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void publishDocumentUploaded(BookingDocument document) {
        publish("document.uploaded", document);
    }

    @Override
    public void publishDocumentReady(BookingDocument document) {
        publish("document.ready", document);
    }

    private void publish(String eventType, BookingDocument document) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            log.debug("Kafka désactivé - événement {} non publié pour documentId={}", eventType, document.getId());
            return;
        }

        DocumentEvent event = new DocumentEvent(
                eventType,
                document.getId(),
                document.getBookingId(),
                document.getClientId(),
                document.getLawyerId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStoragePath(),
                LocalDateTime.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, document.getBookingId().toString(), payload);
            log.info("Événement Kafka publié : type={}, documentId={}, bookingId={}, topic={}",
                    eventType, document.getId(), document.getBookingId(), TOPIC);
        } catch (JsonProcessingException e) {
            log.error("Échec de sérialisation de l'événement {} pour documentId={}",
                    eventType, document.getId(), e);
        }
    }
}