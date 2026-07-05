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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de BookingDocumentService, validation du fichier
 * (taille, type), règles métier (appartenance, statut de réservation),
 * cas nominal de stockage + publication.
 *
 * Le vrai `Files.move`/`transferTo` sur disque n'est volontairement pas
 * testé ici en profondeur (dépendrait du système de fichiers réel),
 * on vérifie que le fichier est bien accepté/rejeté AVANT d'atteindre
 * cette étape, via un dossier temporaire JUnit (@TempDir) pour les cas
 * qui doivent réussir jusqu'au bout.
 */
@ExtendWith(MockitoExtension.class)
class BookingDocumentServiceTest {

    @Mock private BookingDocumentRepository bookingDocumentRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private DocumentEventPublisher documentEventPublisher;

    @InjectMocks
    private BookingDocumentService bookingDocumentService;

    private static final Long CLIENT_ID = 42L;
    private static final Long LAWYER_ID = 4L;
    private static final Long BOOKING_ID = 7L;

    private Booking confirmedBooking;

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        ReflectionTestUtils.setField(bookingDocumentService, "uploadDir", tempDir.toString());

        confirmedBooking = new Booking();
        confirmedBooking.setId(BOOKING_ID);
        confirmedBooking.setClientId(CLIENT_ID);
        confirmedBooking.setLawyerId(LAWYER_ID);
        confirmedBooking.setStatus(BookingStatus.CONFIRMED);
    }

    private MultipartFile pdfFile(String filename, int sizeBytes) {
        return new MockMultipartFile("file", filename, "application/pdf", new byte[sizeBytes]);
    }

    @Nested
    @DisplayName("Validation du fichier")
    class FileValidation {

        @Test
        @DisplayName("fichier vide - rejeté avant tout accès réseau/base")
        void uploadDocument_emptyFile_throwsDocumentUploadException() {
            MultipartFile empty = new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, empty))
                    .isInstanceOf(DocumentUploadException.class)
                    .hasMessageContaining("vide");

            verifyNoInteractions(bookingRepository, documentEventPublisher);
        }

        @Test
        @DisplayName("fichier trop volumineux (> 10 Mo) - rejeté")
        void uploadDocument_fileTooLarge_throwsDocumentUploadException() {
            MultipartFile tooLarge = pdfFile("gros.pdf", 11 * 1024 * 1024);

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, tooLarge))
                    .isInstanceOf(DocumentUploadException.class)
                    .hasMessageContaining("10 Mo");

            verifyNoInteractions(bookingRepository);
        }

        @Test
        @DisplayName("type non autorisé (.docx) — rejeté")
        void uploadDocument_disallowedContentType_throwsDocumentUploadException() {
            MultipartFile docx = new MockMultipartFile("file", "contrat.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[100]);

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, docx))
                    .isInstanceOf(DocumentUploadException.class)
                    .hasMessageContaining("non autorisé");

            verifyNoInteractions(bookingRepository);
        }

        @ParameterizedTest
        @DisplayName("JPEG et PNG sont acceptés en plus du PDF")
        @org.junit.jupiter.params.provider.ValueSource(strings = {"image/jpeg", "image/png", "application/pdf"})
        void uploadDocument_allowedContentTypes_passValidation(String contentType) {
            MultipartFile file = new MockMultipartFile("file", "piece." + contentType.split("/")[1], contentType, new byte[100]);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
            when(bookingDocumentRepository.save(any(BookingDocument.class))).thenAnswer(inv -> {
                BookingDocument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            DocumentUploadResponse response = bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, file);

            assertThat(response.id()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Règles métier sur la réservation")
    class BookingRules {

        @Test
        @DisplayName("réservation introuvable - 404")
        void uploadDocument_bookingNotFound_throwsBookingNotFound() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, pdfFile("a.pdf", 100)))
                    .isInstanceOf(BookingNotFoundException.class);

            verifyNoInteractions(documentEventPublisher);
        }

        @Test
        @DisplayName("réservation n'appartenant pas au client - 403")
        void uploadDocument_notOwner_throwsAccessDenied() {
            confirmedBooking.setClientId(999L);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, pdfFile("a.pdf", 100)))
                    .isInstanceOf(AccessDeniedException.class);

            verify(bookingDocumentRepository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("réservation CANCELLED ou COMPLETED - rejetée")
        @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "COMPLETED"})
        void uploadDocument_terminalStatus_throwsDocumentUploadException(BookingStatus status) {
            confirmedBooking.setStatus(status);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));

            assertThatThrownBy(() -> bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, pdfFile("a.pdf", 100)))
                    .isInstanceOf(DocumentUploadException.class)
                    .hasMessageContaining(status.name());

            verify(bookingDocumentRepository, never()).save(any());
        }

        @ParameterizedTest
        @DisplayName("réservation PENDING ou CONFIRMED - acceptée")
        @EnumSource(value = BookingStatus.class, names = {"PENDING", "CONFIRMED"})
        void uploadDocument_activeStatus_succeeds(BookingStatus status) {
            confirmedBooking.setStatus(status);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
            when(bookingDocumentRepository.save(any(BookingDocument.class))).thenAnswer(inv -> {
                BookingDocument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            DocumentUploadResponse response = bookingDocumentService.uploadDocument(CLIENT_ID, BOOKING_ID, pdfFile("a.pdf", 100));

            assertThat(response.status()).isEqualTo("UPLOADED");
            verify(documentEventPublisher).publishDocumentUploaded(any(BookingDocument.class));
        }
    }

    @Test
    @DisplayName("cas nominal complet - document persisté avec les bonnes métadonnées, événement publié")
    void uploadDocument_happyPath_savesCorrectMetadataAndPublishes() {
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(confirmedBooking));
        when(bookingDocumentRepository.save(any(BookingDocument.class))).thenAnswer(inv -> {
            BookingDocument d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        DocumentUploadResponse response = bookingDocumentService.uploadDocument(
                CLIENT_ID, BOOKING_ID, pdfFile("contrat.pdf", 2048));

        assertThat(response.filename()).isEqualTo("contrat.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.sizeBytes()).isEqualTo(2048);
        assertThat(response.status()).isEqualTo("UPLOADED");

        verify(bookingDocumentRepository).save(argThat(doc ->
                doc.getBookingId().equals(BOOKING_ID)
                && doc.getClientId().equals(CLIENT_ID)
                && doc.getLawyerId().equals(LAWYER_ID)
        ));
    }
}