package juribook.booking_service.repository;

import jakarta.persistence.LockModeType;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    // Tous les créneaux d'un avocat, triés chronologiquement
    List<TimeSlot> findByLawyerIdOrderByDateAscStartTimeAsc(Long lawyerId);

    // Créneaux disponibles d'un avocat à partir d'une date donnée
    // (utilisé pour afficher les créneaux réservables côté client - Sprint 3.2)
    @Query("""
        SELECT t FROM TimeSlot t
        WHERE t.lawyerId = :lawyerId
          AND t.status = juribook.booking_service.entity.SlotStatus.AVAILABLE
          AND t.date >= :fromDate
        ORDER BY t.date ASC, t.startTime ASC
    """)
    List<TimeSlot> findAvailableSlots(
        @Param("lawyerId") Long lawyerId,
        @Param("fromDate") LocalDate fromDate
    );

    // Créneaux d'un avocat sur une date précise, pour vérifier les chevauchements
    // avant de générer/insérer un nouveau créneau
    List<TimeSlot> findByLawyerIdAndDate(Long lawyerId, LocalDate date);

    // Créneaux issus d'une disponibilité récurrente donnée
    List<TimeSlot> findByAvailabilityId(Long availabilityId);

    // Recherche d'un créneau précis pour éviter les doublons lors de la génération
    Optional<TimeSlot> findByLawyerIdAndDateAndStartTime(Long lawyerId, LocalDate date, LocalTime startTime);

    // Créneaux par statut (ex: lister les créneaux BOOKED pour un avocat)
    List<TimeSlot> findByLawyerIdAndStatus(Long lawyerId, SlotStatus status);

    // ── Sprint 3.3 - gestion des créneaux ponctuels et blocages ──
    // Créneaux AVAILABLE d'un avocat sur une plage de dates inclusive,
    // utilisés pour le blocage en masse (congés/indisponibilité)
    @Query("""
        SELECT t FROM TimeSlot t
        WHERE t.lawyerId = :lawyerId
          AND t.status = juribook.booking_service.entity.SlotStatus.AVAILABLE
          AND t.date BETWEEN :fromDate AND :toDate
        ORDER BY t.date ASC, t.startTime ASC
    """)
    List<TimeSlot> findAvailableSlotsInRange(
        @Param("lawyerId") Long lawyerId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate")   LocalDate toDate
    );

    // Tous les créneaux d'un avocat sur une plage de dates, quel que soit
    // le statut, utilisé pour la consultation filtrée (GET /slots)
    @Query("""
        SELECT t FROM TimeSlot t
        WHERE t.lawyerId = :lawyerId
          AND t.date BETWEEN :fromDate AND :toDate
        ORDER BY t.date ASC, t.startTime ASC
    """)
    List<TimeSlot> findByLawyerIdInRange(
        @Param("lawyerId") Long lawyerId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate")   LocalDate toDate
    );

    // ── Sprint 4.3 - protection anti-concurrence sur la réservation ──

    /**
     * Récupère un créneau avec un verrou pessimiste en écriture
     * (SELECT ... FOR UPDATE).
     *
     * Utilisé exclusivement par BookingService.createBooking : sérialise
     * deux requêtes de réservation concurrentes sur le même créneau, la
     * seconde transaction attend que la première se termine (commit ou
     * rollback) avant de pouvoir lire la ligne, et voit donc le statut à
     * jour (BOOKED) plutôt qu'une version périmée encore AVAILABLE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.id = :id")
    Optional<TimeSlot> findByIdForUpdate(@Param("id") Long id);
}