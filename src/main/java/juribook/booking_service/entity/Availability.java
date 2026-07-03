package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Disponibilité récurrente déclarée par un avocat.
 *
 * Représente une plage horaire répétée chaque semaine sur un jour donné
 * (ex : "tous les lundis de 9h00 à 17h00"). C'est le modèle source à
 * partir duquel les TimeSlot concrets sont générés (Sprint 3.3 : génération
 * automatique des créneaux sur une fenêtre glissante, ex: 4 semaines à venir).
 *
 * Principe microservices : lawyerId référence l'entité Lawyer du
 * lawyer-service par son id technique — pas de jointure inter-services,
 * pas de FK en base, juste une colonne de corrélation.
 *
 * Relations :
 *   Availability ──< génère >── TimeSlot (1 Availability produit N TimeSlot)
 *   Ce lien n'est PAS une FK JPA, TimeSlot stocke un availabilityId nullable,
 *   nullable car un TimeSlot peut aussi être créé ponctuellement sans
 *   Availability source (créneau exceptionnel ajouté manuellement).
 */
@Entity
@Table(name = "availabilities")
@Data
public class Availability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Lien vers le lawyer-service ──────────────────────────
    // Id technique de l'avocat (entité Lawyer du lawyer-service).
    // Pas de FK, corrélation logique entre microservices.
    @Column(name = "lawyer_id", nullable = false)
    private Long lawyerId;

    // ── Récurrence hebdomadaire ───────────────────────────────
    // Jour de la semaine concerné par cette plage de disponibilité.
    // java.time.DayOfWeek est mappé en chaîne (MONDAY, TUESDAY, ...)
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    // Heure de début de la plage (ex: 09:00)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    // Heure de fin de la plage (ex: 17:00)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Durée d'un créneau individuel généré à partir de cette disponibilité,
    // en minutes (ex: 30 → des créneaux de 30 min entre startTime et endTime)
    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes = 30;

    // ── Statut et fenêtre de validité ─────────────────────────
    // Permet de désactiver une disponibilité récurrente sans la supprimer
    // (ex: l'avocat part en congé, on coupe la génération de nouveaux créneaux
    // sans perdre l'historique de configuration).
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // Date à partir de laquelle cette disponibilité s'applique (incluse).
    // Permet de programmer un changement d'horaires à l'avance.
    @Column(name = "valid_from")
    private java.time.LocalDate validFrom;

    // Date jusqu'à laquelle cette disponibilité s'applique (incluse, nullable
    // = pas de date de fin, récurrence indéfinie).
    @Column(name = "valid_until")
    private java.time.LocalDate validUntil;

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