package juribook.booking_service.repository;

import juribook.booking_service.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Historique des réservations d'un client. Le tri par
    // date/heure de rendez-vous se fait en service (nécessite TimeSlot,
    // pas disponible ici via une simple dérivation de nom de méthode).
    List<Booking> findByClientId(Long clientId);
}