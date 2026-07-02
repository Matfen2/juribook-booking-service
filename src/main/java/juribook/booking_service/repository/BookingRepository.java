package juribook.booking_service.repository;

import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Historique des réservations d'un client.
    List<Booking> findByClientId(Long clientId);

    // Tableau de bord des réservations d'un avocat.
    List<Booking> findByLawyerId(Long lawyerId);

    // Candidats au rappel 24h
    List<Booking> findByStatusAndReminderSentFalse(BookingStatus status);
}