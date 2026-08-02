package juribook.booking_service.service;

import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingDocument;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.dto.response.DocumentUploadResponse;
import juribook.booking_service.event.DocumentEventPublisher;
import juribook.booking_service.exception.BookingNotFoundException;
import juribook.booking_service.exception.DocumentUploadException;
import juribook.booking_service.repository.BookingDocumentRepository;
import juribook.booking_service.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Upload de documents avant consultation (Sprint 6.6).
 *
 * Stockage sur disque local temporaire (app.upload-dir) — pas de
 * stockage permanent (S3 ou équivalent) à ce stade, c'est explicitement
 * le rôle du futur traitement asynchrone (Sprint 6.7 : scan antivirus,
 * déplacement vers stockage permanent, publication document.ready).
 * Ce service ne fait que recevoir, valider, stocker temporairement, et
 * publier l'événement — traitement 100% synchrone côté upload lui-même.
 *
 * Règle métier : autorisé sur une réservation PENDING ou CONFIRMED
 * uniquement — envoyer une pièce à un rendez-vous déjà annulé ou déjà
 * passé (COMPLETED) n'a pas de sens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 Mo
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );

    private final BookingDocumentRepository bookingDocumentRepository;
    private final BookingRepository bookingRepository;
    private final DocumentEventPublisher documentEventPublisher;

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Transactional
    public DocumentUploadResponse uploadDocument(Long clientId, Long bookingId, MultipartFile file) {
        validateFile(file);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(
                    "Réservation introuvable : id=" + bookingId));

        if (!clientId.equals(booking.getClientId())) {
            throw new AccessDeniedException("Cette réservation ne vous appartient pas");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new DocumentUploadException(
                "Impossible d'ajouter un document à une réservation " + booking.getStatus());
        }

        String storedFilename = bookingId + "_" + UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetPath = Paths.get(uploadDir).resolve(storedFilename);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
        } catch (IOException e) {
            log.error("Échec de l'écriture du fichier sur disque pour bookingId={}", bookingId, e);
            throw new DocumentUploadException("Échec de l'enregistrement du fichier, réessayez");
        }

        BookingDocument document = new BookingDocument();
        document.setBookingId(bookingId);
        document.setClientId(clientId);
        document.setLawyerId(booking.getLawyerId());
        document.setOriginalFilename(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setStoragePath(targetPath.toString());

        BookingDocument saved = bookingDocumentRepository.save(document);
        log.info("Document uploadé : id={}, bookingId={}, filename={}, size={}",
                saved.getId(), bookingId, file.getOriginalFilename(), file.getSize());

        documentEventPublisher.publishDocumentUploaded(saved);

        return DocumentUploadResponse.from(saved);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException("Le fichier est vide ou manquant");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new DocumentUploadException("Le fichier dépasse la taille maximale autorisée (10 Mo)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new DocumentUploadException(
                "Type de fichier non autorisé : " + contentType + " (formats acceptés : PDF, JPEG, PNG)");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "fichier";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}