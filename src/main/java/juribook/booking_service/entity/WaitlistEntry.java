package juribook.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Inscription d'un client sur la liste d'attente d'un avocat complet.
 *
 * Principe microservices : lawyerId et clientId référencent respectivement
 * l'entité Lawyer du lawyer-service et l'entité User de l'auth-service par
 * leur id technique — pas de jointure inter-services, pas de FK en base,
 * simples colonnes de corrélation (même principe que Booking).
 *
 * Un client ne peut s'inscrire qu'une fois par avocat (contrainte UNIQUE
 * lawyer_id + client_id en base, cf. V6__create_waitlist_entries_table.sql).
 */
@Entity
@Table(name = "waitlist_entries")
@Data
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lawyer_id", nullable = false)
    private Long lawyerId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}