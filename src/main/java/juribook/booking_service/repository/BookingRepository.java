package juribook.booking_service.repository;

import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByClientId(Long clientId);

    List<Booking> findByLawyerId(Long lawyerId);

    // Candidats au rappel 24h.
    List<Booking> findByStatusAndReminderSentFalse(BookingStatus status);

    // Réservations PENDING à annuler automatiquement quand
    // l'avocat devient indisponible.
    List<Booking> findByLawyerIdAndStatus(Long lawyerId, BookingStatus status);
}