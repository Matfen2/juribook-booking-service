package juribook.booking_service.repository;

import juribook.booking_service.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    // Toutes les disponibilités déclarées par un avocat
    List<Availability> findByLawyerId(Long lawyerId);

    // Disponibilités actives d'un avocat, utilisées pour la génération des créneaux
    List<Availability> findByLawyerIdAndActiveTrue(Long lawyerId);

    // Disponibilité d'un avocat pour un jour de semaine donné (vérification de chevauchement)
    List<Availability> findByLawyerIdAndDayOfWeek(Long lawyerId, DayOfWeek dayOfWeek);
}