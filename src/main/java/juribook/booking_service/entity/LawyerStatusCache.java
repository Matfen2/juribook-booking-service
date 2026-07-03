package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Cache local de "cet avocat accepte-t-il de nouvelles réservations",
 * synchronisé via l'événement Kafka lawyer.status-changed.
 *
 * Volontairement une entité séparée de TimeSlot/Availability, ce n'est
 * pas une donnée métier propre à booking-service, c'est une simple
 * projection en lecture seule de l'état réel (Lawyer.available côté
 * lawyer-service). Pattern CQRS / eventual consistency (UC-K4 du
 * cahier des charges) : lu de façon synchrone et locale à chaque
 * réservation, jamais par appel réseau direct au lawyer-service.
 *
 * lawyerId est directement la clé primaire, pas de @GeneratedValue —
 * une seule ligne par avocat, mise à jour (upsert) à chaque événement.
 */
@Entity
@Table(name = "lawyer_status_cache")
@Data
public class LawyerStatusCache {

    @Id
    @Column(name = "lawyer_id")
    private Long lawyerId;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}