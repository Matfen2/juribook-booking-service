package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Réservation d'un TimeSlot par un client.
 *
 * Relie un client, un avocat et un créneau concret, avec un motif de
 * consultation et un statut suivant le cycle PENDING → CONFIRMED →
 * COMPLETED / CANCELLED (cf. BookingStatus).
 *
 * Principe microservices : clientId et lawyerId référencent respectivement
 * les entités User (auth-service) et Lawyer (lawyer-service) par leur id
 * technique, pas de jointure inter-services, pas de FK en base, juste des
 * colonnes de corrélation.
 *
 * timeSlotId référence un TimeSlot du même service (même base de données),
 * mais volontairement sans @ManyToOne JPA, pour rester cohérent avec le
 * choix déjà fait côté TimeSlot.bookingId : ça permet de faire évoluer les
 * deux entités indépendamment et d'éviter un couplage de suppression en
 * cascade. La cohérence (un seul Booking actif par créneau, transition de
 * statut du TimeSlot associé) est gérée au niveau service.
 */
@Entity
@Table(name = "bookings")
@Data
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Lien vers l'auth-service ──────────────────────────────
    // Id technique du client (entité User, rôle CLIENT, auth-service).
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    // ── Lien vers le lawyer-service ──────────────────────────
    // Id technique de l'avocat (entité Lawyer, lawyer-service).
    // Dénormalisé depuis le TimeSlot réservé pour permettre une recherche
    // directe "toutes les réservations de l'avocat X" sans jointure.
    @Column(name = "lawyer_id", nullable = false)
    private Long lawyerId;

    // ── Lien vers le créneau réservé ──────────────────────────
    // Id technique du TimeSlot (booking-service, même base, pas de FK
    // JPA stricte, cf. note de classe).
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    // ── Statut et motif ────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private BookingStatus status = BookingStatus.PENDING;

    // Motif de consultation renseigné par le client à la réservation
    // (ex: "Litige avec mon employeur", "Rédaction de contrat")
    @Column(name = "reason", length = 500)
    private String reason;

    // ── Rappel automatique ───────────────────────
    // Évite que BookingReminderJob renvoie plusieurs fois le rappel 24h
    // pour la même réservation entre deux exécutions du job planifié.
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    // ── Audit ──────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}