package juribook.booking_service.service;

import juribook.booking_service.entity.BookingDocument;
import juribook.booking_service.entity.DocumentStatus;
import juribook.booking_service.event.DocumentEventPublisher;
import juribook.booking_service.repository.BookingDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Traitement asynchrone d'un document uploadé, déclenché
 * par DocumentEventConsumer sur réception de document.uploaded.
 *
 * Deux étapes simulées, faute d'intégration réelle disponible dans cet
 * environnement :
 *   1. "Scan antivirus", STUB, toujours "propre". Aucune intégration
 *      réelle (ClamAV ou équivalent) à ce stade. À remplacer avant
 *      toute mise en production.
 *   2. "Stockage permanent", un second dossier local (app.permanent-
 *      storage-dir), distinct du dossier temporaire de l'upload. Fait
 *      office de stand-in pour un vrai stockage cloud (S3 ou
 *      équivalent), dont l'intégration réelle est repoussée.
 *
 * Le fichier n'est déplacé, et document.ready n'est publié, que si les
 * deux étapes réussissent, un échec quelconque laisse le document en
 * UPLOADED, sans notification au client (pas de mécanisme de nouvelle
 * tentative pour l'instant, cf. limites connues).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private final BookingDocumentRepository bookingDocumentRepository;
    private final DocumentEventPublisher documentEventPublisher;

    @Value("${app.permanent-storage-dir}")
    private String permanentStorageDir;

    @Transactional
    public void processDocument(Long documentId) {
        BookingDocument document = bookingDocumentRepository.findById(documentId).orElse(null);

        if (document == null) {
            log.warn("Document introuvable pour le traitement asynchrone : documentId={}", documentId);
            return;
        }

        // Idempotence : si ce message est retraité (redémarrage du
        // consumer avant commit d'offset, par exemple), on ne retraite
        // pas un document déjà passé en READY.
        if (document.getStatus() != DocumentStatus.UPLOADED) {
            log.debug("Document déjà traité, ignoré : documentId={}, status={}",
                    documentId, document.getStatus());
            return;
        }

        if (!simulateAntivirusScan(document)) {
            log.warn("Fichier rejeté au scan antivirus (simulation) : documentId={}", documentId);
            return;
        }

        Path permanentPath;
        try {
            permanentPath = moveToPermanentStorage(document);
        } catch (IOException e) {
            log.error("Échec du déplacement vers le stockage permanent : documentId={}", documentId, e);
            return;
        }

        document.setStoragePath(permanentPath.toString());
        document.setStatus(DocumentStatus.READY);
        BookingDocument saved = bookingDocumentRepository.save(document);

        log.info("Document traité : documentId={}, nouveau chemin={}", documentId, permanentPath);
        documentEventPublisher.publishDocumentReady(saved);
    }

    private boolean simulateAntivirusScan(BookingDocument document) {
        log.debug("[SCAN STUB] Analyse antivirus simulée pour documentId={}, filename={} — "
                + "toujours \"propre\", aucune intégration réelle à ce stade",
                document.getId(), document.getOriginalFilename());
        return true;
    }

    private Path moveToPermanentStorage(BookingDocument document) throws IOException {
        Path currentPath = Paths.get(document.getStoragePath());
        Path permanentPath = Paths.get(permanentStorageDir).resolve(currentPath.getFileName());

        Files.createDirectories(permanentPath.getParent());
        Files.move(currentPath, permanentPath, StandardCopyOption.REPLACE_EXISTING);

        return permanentPath;
    }
}