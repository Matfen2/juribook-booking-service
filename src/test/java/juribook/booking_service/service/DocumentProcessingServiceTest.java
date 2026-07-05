package juribook.booking_service.service;

import juribook.booking_service.entity.BookingDocument;
import juribook.booking_service.entity.DocumentStatus;
import juribook.booking_service.event.DocumentEventPublisher;
import juribook.booking_service.repository.BookingDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de DocumentProcessingService : le scan antivirus
 * étant un stub toujours "propre", ces tests couvrent surtout
 * l'idempotence et le comportement sur échec du déplacement de fichier,
 * pas le scan lui-même (rien à brancher dessus).
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock private BookingDocumentRepository bookingDocumentRepository;
    @Mock private DocumentEventPublisher documentEventPublisher;

    @InjectMocks
    private DocumentProcessingService documentProcessingService;

    private Path uploadDir;
    private Path permanentDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        uploadDir = tempDir.resolve("uploads");
        permanentDir = tempDir.resolve("permanent");
        Files.createDirectories(uploadDir);

        ReflectionTestUtils.setField(documentProcessingService, "permanentStorageDir", permanentDir.toString());
    }

    private BookingDocument uploadedDocument(Path fileOnDisk) {
        BookingDocument doc = new BookingDocument();
        doc.setId(1L);
        doc.setBookingId(7L);
        doc.setClientId(42L);
        doc.setLawyerId(4L);
        doc.setOriginalFilename("contrat.pdf");
        doc.setContentType("application/pdf");
        doc.setSizeBytes(100);
        doc.setStoragePath(fileOnDisk.toString());
        doc.setStatus(DocumentStatus.UPLOADED);
        return doc;
    }

    @Test
    @DisplayName("cas nominal - déplace le fichier, passe en READY, publie document.ready")
    void processDocument_happyPath_movesFileAndPublishesReady() throws IOException {
        Path original = uploadDir.resolve("7_uuid_contrat.pdf");
        Files.writeString(original, "contenu du fichier");

        BookingDocument document = uploadedDocument(original);
        when(bookingDocumentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(bookingDocumentRepository.save(any(BookingDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        documentProcessingService.processDocument(1L);

        assertThat(Files.exists(original)).isFalse(); // plus dans le dossier temporaire
        assertThat(Files.exists(permanentDir.resolve("7_uuid_contrat.pdf"))).isTrue(); // déplacé

        ArgumentCaptor<BookingDocument> captor = ArgumentCaptor.forClass(BookingDocument.class);
        verify(bookingDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(captor.getValue().getStoragePath()).contains("permanent");

        verify(documentEventPublisher).publishDocumentReady(any(BookingDocument.class));
    }

    @Test
    @DisplayName("document introuvable - ne fait rien, ne plante pas")
    void processDocument_documentNotFound_doesNothing() {
        when(bookingDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        documentProcessingService.processDocument(999L);

        verify(bookingDocumentRepository, never()).save(any());
        verifyNoInteractions(documentEventPublisher);
    }

    @Test
    @DisplayName("document déjà READY - idempotent, ne retraite pas")
    void processDocument_alreadyReady_skipsReprocessing() {
        BookingDocument alreadyReady = uploadedDocument(uploadDir.resolve("whatever.pdf"));
        alreadyReady.setStatus(DocumentStatus.READY);
        when(bookingDocumentRepository.findById(1L)).thenReturn(Optional.of(alreadyReady));

        documentProcessingService.processDocument(1L);

        verify(bookingDocumentRepository, never()).save(any());
        verifyNoInteractions(documentEventPublisher);
    }

    @Test
    @DisplayName("fichier source manquant sur disque - échec du déplacement, reste UPLOADED, pas de publication")
    void processDocument_fileMissingOnDisk_staysUploaded() {
        Path missingFile = uploadDir.resolve("fichier_qui_n_existe_pas.pdf");
        BookingDocument document = uploadedDocument(missingFile);
        when(bookingDocumentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentProcessingService.processDocument(1L);

        verify(bookingDocumentRepository, never()).save(any());
        verifyNoInteractions(documentEventPublisher);
    }
}