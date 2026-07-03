package juribook.booking_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.booking_service.entity.LawyerStatusCache;
import juribook.booking_service.repository.LawyerStatusCacheRepository;
import juribook.booking_service.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Consomme lawyer-events.
 *
 * 1. Met à jour LawyerStatusCache, lu par
 *    BookingService.createBooking pour refuser les nouvelles demandes.
 * 2. Si l'avocat devient indisponible, déclenche l'annulation
 *    automatique de toutes ses réservations encore PENDING,
 *    synchronisation inter-services, cohérence éventuelle (UC-K4).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LawyerEventConsumer {

    private final LawyerStatusCacheRepository lawyerStatusCacheRepository;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "lawyer-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onLawyerEvent(String payload) {
        LawyerEvent event;
        try {
            event = objectMapper.readValue(payload, LawyerEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Impossible de désérialiser un message du topic lawyer-events : {}", payload, e);
            return;
        }

        if (!"lawyer.status-changed".equals(event.eventType()) || event.lawyerId() == null) {
            log.debug("Événement lawyer-events ignoré (eventType={})", event.eventType());
            return;
        }

        boolean available = Boolean.TRUE.equals(event.available());

        LawyerStatusCache cache = lawyerStatusCacheRepository.findById(event.lawyerId())
                .orElseGet(LawyerStatusCache::new);
        cache.setLawyerId(event.lawyerId());
        cache.setAvailable(available);
        cache.setUpdatedAt(LocalDateTime.now());

        lawyerStatusCacheRepository.save(cache);
        log.info("Cache de statut avocat mis à jour : lawyerId={}, available={}",
                event.lawyerId(), available);

        // Synchronisation, un avocat qui devient
        // indisponible ne doit plus avoir de demandes en attente de sa
        // réponse, elles sont annulées automatiquement.
        if (!available) {
            bookingService.cancelPendingBookingsForInactiveLawyer(event.lawyerId());
        }
    }
}