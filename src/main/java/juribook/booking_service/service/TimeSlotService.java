package juribook.booking_service.service;

import juribook.booking_service.dto.request.BlockPeriodRequest;
import juribook.booking_service.dto.request.CreateTimeSlotRequest;
import juribook.booking_service.dto.response.BlockPeriodResponse;
import juribook.booking_service.dto.response.TimeSlotResponse;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.InvalidTimeSlotException;
import juribook.booking_service.exception.TimeSlotNotFoundException;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Service métier pour la gestion ponctuelle des créneaux (Sprint 3.3).
 *
 * Trois familles d'opérations :
 *   1. Créneaux ponctuels  - ajout/suppression hors récurrence
 *   2. Blocage de période  - congés, indisponibilités sur une plage de dates
 *   3. Déblocage           - annule un blocage, remet AVAILABLE
 *
 * Règles métier centrales :
 *   - on ne supprime ni ne bloque jamais un créneau BOOKED ou COMPLETED
 *     (protège l'historique et les réservations déjà engagées)
 *   - un créneau ponctuel ajouté ne doit pas chevaucher un créneau existant
 *     du même avocat à la même date (vérifié en amont, et protégé en dernier
 *     recours par la contrainte UNIQUE(lawyer_id, date, start_time) en BDD)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    // ══════════════════════════════════════════════════════════
    //  Créneaux ponctuels
    // ══════════════════════════════════════════════════════════
    @Transactional
    public TimeSlotResponse createTimeSlot(Long lawyerId, CreateTimeSlotRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime());
        validateNotInPast(request.getDate(), request.getStartTime());
        checkNoOverlap(lawyerId, request.getDate(), request.getStartTime(), request.getEndTime());

        TimeSlot slot = new TimeSlot();
        slot.setLawyerId(lawyerId);
        slot.setAvailabilityId(null); // créneau ponctuel - pas de récurrence source
        slot.setDate(request.getDate());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setStatus(SlotStatus.AVAILABLE);

        try {
            TimeSlot saved = timeSlotRepository.save(slot);
            log.info("Créneau ponctuel créé : id={}, lawyerId={}, {} {}-{}",
                    saved.getId(), lawyerId, saved.getDate(), saved.getStartTime(), saved.getEndTime());
            return TimeSlotResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité si la contrainte UNIQUE de la BDD est déclenchée
            // malgré la vérification applicative (cas de concurrence)
            throw new InvalidTimeSlotException(
                "Un créneau existe déjà à cette date et cette heure pour cet avocat");
        }
    }

    @Transactional
    public void deleteTimeSlot(Long lawyerId, Long slotId) {
        TimeSlot slot = getOwnedSlot(lawyerId, slotId);

        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new InvalidTimeSlotException(
                "Impossible de supprimer un créneau réservé — annulez d'abord la réservation");
        }
        if (slot.getStatus() == SlotStatus.COMPLETED) {
            throw new InvalidTimeSlotException(
                "Impossible de supprimer un créneau déjà honoré (historique)");
        }

        timeSlotRepository.delete(slot);
        log.info("Créneau supprimé : id={}, lawyerId={}", slotId, lawyerId);
    }

    // ══════════════════════════════════════════════════════════
    //  Blocage de période (congés, indisponibilités)
    // ══════════════════════════════════════════════════════════
    @Transactional
    public BlockPeriodResponse blockPeriod(Long lawyerId, BlockPeriodRequest request) {
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new InvalidTimeSlotException(
                "La date de fin doit être après ou égale à la date de début");
        }

        List<TimeSlot> toBlock = timeSlotRepository.findAvailableSlotsInRange(
                lawyerId, request.getFromDate(), request.getToDate());

        toBlock.forEach(slot -> {
            slot.setStatus(SlotStatus.BLOCKED);
            slot.setBlockReason(request.getReason());
        });
        timeSlotRepository.saveAll(toBlock);

        log.info("Période bloquée : lawyerId={}, {} → {}, {} créneaux bloqués, motif={}",
                lawyerId, request.getFromDate(), request.getToDate(), toBlock.size(), request.getReason());

        return BlockPeriodResponse.builder()
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .reason(request.getReason())
                .blockedSlotsCount(toBlock.size())
                .blockedSlots(toBlock.stream().map(TimeSlotResponse::from).toList())
                .build();
    }

    @Transactional
    public TimeSlotResponse unblockSlot(Long lawyerId, Long slotId) {
        TimeSlot slot = getOwnedSlot(lawyerId, slotId);

        if (slot.getStatus() != SlotStatus.BLOCKED) {
            throw new InvalidTimeSlotException(
                "Ce créneau n'est pas bloqué (statut actuel : " + slot.getStatus() + ")");
        }

        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setBlockReason(null);
        TimeSlot saved = timeSlotRepository.save(slot);

        log.info("Créneau débloqué : id={}, lawyerId={}", slotId, lawyerId);
        return TimeSlotResponse.from(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  Consultation
    // ══════════════════════════════════════════════════════════
    /**
     * Liste les créneaux d'un avocat avec filtres optionnels.
     *
     * Priorité de date : si `date` est fourni, il prime sur fromDate/toDate
     * et borne la recherche à cette seule journée, usage typique de l'API
     * publique (Sprint 3.4) : GET /api/lawyers/{id}/slots?date=2026-05-15.
     *
     * Filtre de statut par défaut : si aucun `status` n'est fourni ET que
     * `date` est utilisé (cas de la consultation publique), seuls les
     * créneaux AVAILABLE sont retournés, un client cherchant un rendez-vous
     * n'a pas à voir les créneaux BOOKED ou BLOCKED des autres clients.
     * Si `status` est fourni explicitement, il prime toujours (permet à
     * l'avocat ou à l'admin de consulter d'autres statuts).
     */
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getSlots(Long lawyerId, LocalDate date,
                                           LocalDate fromDate, LocalDate toDate,
                                           SlotStatus status) {
        LocalDate from;
        LocalDate to;
        boolean defaultToAvailableOnly;

        if (date != null) {
            from = date;
            to = date;
            defaultToAvailableOnly = true;
        } else {
            from = fromDate != null ? fromDate : LocalDate.now();
            to   = toDate   != null ? toDate   : from.plusMonths(1);
            defaultToAvailableOnly = false;
        }

        List<TimeSlot> slots = timeSlotRepository.findByLawyerIdInRange(lawyerId, from, to);

        SlotStatus effectiveStatus = status != null
                ? status
                : (defaultToAvailableOnly ? SlotStatus.AVAILABLE : null);

        return slots.stream()
                .filter(s -> effectiveStatus == null || s.getStatus() == effectiveStatus)
                .map(TimeSlotResponse::from)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers privés
    // ══════════════════════════════════════════════════════════
    private TimeSlot getOwnedSlot(Long lawyerId, Long slotId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable : id=" + slotId));

        if (!slot.getLawyerId().equals(lawyerId)) {
            throw new InvalidTimeSlotException(
                "Ce créneau n'appartient pas à l'avocat spécifié");
        }
        return slot;
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidTimeSlotException(
                "L'heure de fin doit être après l'heure de début");
        }
    }

    private void validateNotInPast(LocalDate date, LocalTime startTime) {
        LocalDateTime slotStart = LocalDateTime.of(date, startTime);
        if (slotStart.isBefore(LocalDateTime.now())) {
            throw new InvalidTimeSlotException(
                "Impossible de créer un créneau dans le passé");
        }
    }

    private void checkNoOverlap(Long lawyerId, LocalDate date, LocalTime start, LocalTime end) {
        List<TimeSlot> sameDay = timeSlotRepository.findByLawyerIdAndDate(lawyerId, date);

        boolean overlaps = sameDay.stream()
                .anyMatch(existing -> timeRangesOverlap(
                        existing.getStartTime(), existing.getEndTime(), start, end
                ));

        if (overlaps) {
            throw new InvalidTimeSlotException(
                "Ce créneau chevauche un créneau existant à cette date");
        }
    }

    private boolean timeRangesOverlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}