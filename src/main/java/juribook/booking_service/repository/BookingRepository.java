package juribook.booking_service.repository;

import juribook.booking_service.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository minimal pour Booking
 *
 * Sera enrichi dans les sprints suivants : recherche par client, par avocat, vérification de double réservation sur un créneau.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
}