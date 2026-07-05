package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Document envoyé par un client en amont d'un rendez-vous (contrat,
 * convocation...). Stocké temporairement sur disque local,
 * en attente d'un traitement asynchrone qui le déplacera vers un
 * stockage permanent (pas encore implémenté).
 *
 * clientId/lawyerId dénormalisés depuis Booking au moment de l'upload,
 * même principe que partout ailleurs dans ce service.
 */
@Entity
@Table(name = "booking_documents")
@Data
public class BookingDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "lawyer_id", nullable = false)
    private Long lawyerId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    // Chemin sur le disque local, temporaire, cf. commentaire de classe.
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}