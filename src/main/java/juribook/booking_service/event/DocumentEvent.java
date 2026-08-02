package juribook.booking_service.event;

import java.time.LocalDateTime;

/**
 * Payload publié sur document-events à chaque upload, métadonnées
 * uniquement (jamais le contenu du fichier lui-même sur Kafka). Le
 * futur consumer asynchrone ("document-processor") s'en
 * servira pour retrouver le fichier via storagePath, le scanner, le
 * déplacer en stockage permanent, puis publier document.ready.
 */
public record DocumentEvent(
    String eventType,
    Long documentId,
    Long bookingId,
    Long clientId,
    Long lawyerId,
    String originalFilename,
    String contentType,
    long sizeBytes,
    String storagePath,
    LocalDateTime occurredAt
) {
}