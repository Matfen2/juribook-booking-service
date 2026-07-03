package juribook.booking_service.repository;

import juribook.booking_service.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    // Utilisé pour la notification des clients en attente
    List<WaitlistEntry> findByLawyerIdOrderByCreatedAtAsc(Long lawyerId);

    // Vérification anti-doublon avant inscription
    Optional<WaitlistEntry> findByLawyerIdAndClientId(Long lawyerId, Long clientId);
}