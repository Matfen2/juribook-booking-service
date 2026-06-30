package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Créneau concret réservable par un client, lié à un avocat.
 *
 * Contrairement à Availability (récurrence abstraite hebdomadaire),
 * TimeSlot représente une instance datée précise, ex: "lundi 6 juillet
 * 2026, de 9h00 à 9h30, avocat #10, statut AVAILABLE".
 *
 * Deux origines possibles pour un TimeSlot :
 *   1. Généré automatiquement depuis une Availability (availabilityId renseigné)
 *   2. Créé ponctuellement par l'avocat hors récurrence (availabilityId null)
 *      , ex: un avocat ajoute une disponibilité exceptionnelle un samedi.
 *
 * Le cycle de vie est piloté par SlotStatus (cf. enum).
 *
 * Contrainte d'unicité métier : un avocat ne peut pas avoir deux créneaux
 * qui se chevauchent au même moment (vérifié au niveau service, pas en BDD,
 * une contrainte d'exclusion PostgreSQL serait plus robuste mais plus complexe
 * à mettre en place avec Flyway/Hibernate, repoussé si besoin en V2).
 */
@Entity
@Table(name = "time_slots")
@Data
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Lien vers le lawyer-service ──────────────────────────
    // Id technique de l'avocat (entité Lawyer du lawyer-service).
    // Dénormalisé ici (dupliqué depuis Availability si applicable) pour
    // permettre une recherche directe "tous les créneaux de l'avocat X"
    // sans jointure vers availabilities.
    @Column(name = "lawyer_id", nullable = false)
    private Long lawyerId;

    // ── Lien optionnel vers la disponibilité source ──────────
    // Null si le créneau a été créé ponctuellement (hors récurrence).
    // Pas de FK JPA stricte (@ManyToOne) volontairement : permet de
    // supprimer une Availability sans contrainte de suppression en cascade
    // sur des créneaux déjà réservés/passés, l'historique est préservé.
    @Column(name = "availability_id")
    private Long availabilityId;

    // ── Date et horaires du créneau ──────────────────────────
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // ── Statut du créneau ─────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private SlotStatus status = SlotStatus.AVAILABLE;

    // ── Lien vers la réservation (Sprint 3.2) ────────────────
    // Renseigné uniquement quand status = BOOKED.
    // Pointe vers l'id technique d'une future entité Booking
    // (pas de FK, le booking-service est propriétaire des deux tables,
    // mais on garde le découplage logique pour permettre l'évolution
    // indépendante des deux entités).
    @Column(name = "booking_id")
    private Long bookingId;

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

    // ── Helpers métier ───────────────────────────────────────
    /**
     * Un créneau est réservable s'il est AVAILABLE et que sa date/heure
     * de début n'est pas déjà passée.
     */
    public boolean isBookable() {
        if (status != SlotStatus.AVAILABLE) {
            return false;
        }
        LocalDateTime slotStart = LocalDateTime.of(date, startTime);
        return slotStart.isAfter(LocalDateTime.now());
    }
}