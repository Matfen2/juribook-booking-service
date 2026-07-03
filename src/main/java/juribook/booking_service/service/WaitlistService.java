package juribook.booking_service.service;

import juribook.booking_service.dto.response.WaitlistEntryResponse;
import juribook.booking_service.entity.WaitlistEntry;
import juribook.booking_service.exception.AlreadyOnWaitlistException;
import juribook.booking_service.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service métier pour la liste d'attente d'un avocat (Sprint 4.8 + 4.9).
 *
 * ⚠️ Portée assumée pour ce sprint : l'inscription est acceptée sans
 * vérifier que l'avocat est effectivement "complet" (aucun créneau
 * AVAILABLE). Le seul garde-fou est anti-doublon (un client ne peut
 * s'inscrire qu'une fois par avocat).
 *
 * getWaitlist est public (pas de JWT requis, cf. SecurityConfig) et
 * utilisé par le notification-service pour résoudre la liste des
 * clients à notifier quand un événement slot.released est reçu — le
 * booking-service reste seul propriétaire des données de liste
 * d'attente (database per service), le notification-service les
 * consulte via cet endpoint plutôt que d'y accéder directement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    @Transactional
    public WaitlistEntryResponse joinWaitlist(Long lawyerId, Long clientId) {
        if (waitlistRepository.findByLawyerIdAndClientId(lawyerId, clientId).isPresent()) {
            throw new AlreadyOnWaitlistException(
                "Vous êtes déjà inscrit sur la liste d'attente de cet avocat");
        }

        WaitlistEntry entry = new WaitlistEntry();
        entry.setLawyerId(lawyerId);
        entry.setClientId(clientId);

        WaitlistEntry saved;
        try {
            saved = waitlistRepository.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité si la contrainte UNIQUE est déclenchée
            // malgré la vérification applicative (cas de concurrence)
            throw new AlreadyOnWaitlistException(
                "Vous êtes déjà inscrit sur la liste d'attente de cet avocat");
        }

        log.info("Inscription liste d'attente : lawyerId={}, clientId={}", lawyerId, clientId);
        return WaitlistEntryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getWaitlist(Long lawyerId) {
        return waitlistRepository.findByLawyerIdOrderByCreatedAtAsc(lawyerId).stream()
                .map(WaitlistEntryResponse::from)
                .toList();
    }
}