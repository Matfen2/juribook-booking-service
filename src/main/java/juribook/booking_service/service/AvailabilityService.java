package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateAvailabilityRequest;
import juribook.booking_service.dto.response.AvailabilityResponse;
import juribook.booking_service.entity.Availability;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.AvailabilityNotFoundException;
import juribook.booking_service.exception.InvalidAvailabilityException;
import juribook.booking_service.repository.AvailabilityRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Service métier pour les disponibilités récurrentes des avocats.
 *
 * Règle centrale du Sprint 3.2 : la création d'une Availability déclenche
 * IMMÉDIATEMENT la génération des TimeSlot concrets correspondants, sur
 * une fenêtre glissante de N semaines (par défaut DEFAULT_GENERATION_WEEKS).
 *
 * Règles de validation métier :
 *   - endTime doit être après startTime (doublé ici en plus de la contrainte SQL,
 *     pour retourner un message clair avant d'aller en BDD)
 *   - la nouvelle disponibilité ne doit pas chevaucher une disponibilité
 *     existante du même avocat sur le même jour de semaine
 *   - la génération de créneaux ignore silencieusement les créneaux qui
 *     existeraient déjà (idempotence : un appel répété ne crée pas de doublons,
 *     protégé aussi par la contrainte UNIQUE(lawyer_id, date, start_time) en BDD)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final TimeSlotRepository timeSlotRepository;

    private static final int DEFAULT_GENERATION_WEEKS = 4;

    // ── Créer une disponibilité + générer les créneaux ───────
    @Transactional
    public AvailabilityResponse createAvailability(Long lawyerId, CreateAvailabilityRequest request) {

        validateTimeRange(request.getStartTime(), request.getEndTime());
        checkNoOverlap(lawyerId, request);

        Availability availability = new Availability();
        availability.setLawyerId(lawyerId);
        availability.setDayOfWeek(request.getDayOfWeek());
        availability.setStartTime(request.getStartTime());
        availability.setEndTime(request.getEndTime());
        availability.setSlotDurationMinutes(request.getSlotDurationMinutes());
        availability.setActive(true);
        availability.setValidFrom(request.getValidFrom() != null ? request.getValidFrom() : LocalDate.now());
        availability.setValidUntil(request.getValidUntil());

        Availability saved = availabilityRepository.save(availability);
        log.info("Disponibilité créée : id={}, lawyerId={}, {} {}-{}",
                saved.getId(), lawyerId, saved.getDayOfWeek(), saved.getStartTime(), saved.getEndTime());

        int weeks = request.getGenerationWeeks() != null ? request.getGenerationWeeks() : DEFAULT_GENERATION_WEEKS;
        int generatedCount = generateSlotsForAvailability(saved, weeks);

        log.info("{} créneaux générés pour la disponibilité id={}", generatedCount, saved.getId());

        return AvailabilityResponse.from(saved, generatedCount);
    }

    // ── Lister les disponibilités d'un avocat ────────────────
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getByLawyerId(Long lawyerId) {
        return availabilityRepository.findByLawyerId(lawyerId).stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    // ── Désactiver une disponibilité (ne supprime pas les créneaux déjà générés) ──
    @Transactional
    public AvailabilityResponse deactivate(Long lawyerId, Long availabilityId) {
        Availability availability = availabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new AvailabilityNotFoundException(
                    "Disponibilité introuvable : id=" + availabilityId));

        if (!availability.getLawyerId().equals(lawyerId)) {
            throw new InvalidAvailabilityException(
                "Cette disponibilité n'appartient pas à l'avocat spécifié");
        }

        availability.setActive(false);
        Availability saved = availabilityRepository.save(availability);
        log.info("Disponibilité désactivée : id={}, lawyerId={}", availabilityId, lawyerId);

        return AvailabilityResponse.from(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  Génération des créneaux concrets
    // ══════════════════════════════════════════════════════════

    /**
     * Génère les TimeSlot concrets pour une Availability donnée, sur les
     * `weeks` prochaines semaines à partir d'aujourd'hui (ou de validFrom
     * si elle est dans le futur).
     *
     * Idempotent : si un créneau existe déjà pour ce lawyerId/date/startTime,
     * il est ignoré plutôt que de provoquer une erreur de contrainte UNIQUE.
     */
    private int generateSlotsForAvailability(Availability availability, int weeks) {
        LocalDate startSearch = availability.getValidFrom().isAfter(LocalDate.now())
                ? availability.getValidFrom()
                : LocalDate.now();

        LocalDate endSearch = startSearch.plusWeeks(weeks);
        if (availability.getValidUntil() != null && availability.getValidUntil().isBefore(endSearch)) {
            endSearch = availability.getValidUntil();
        }

        int created = 0;
        LocalDate cursor = nextOrSameDayOfWeek(startSearch, availability.getDayOfWeek());

        while (!cursor.isAfter(endSearch)) {
            created += generateSlotsForDate(availability, cursor);
            cursor = cursor.plusWeeks(1);
        }

        return created;
    }

    /**
     * Découpe la plage [startTime, endTime] de l'Availability en créneaux
     * de slotDurationMinutes pour une date donnée, et les persiste.
     */
    private int generateSlotsForDate(Availability availability, LocalDate date) {
        // Évite les doublons si la génération est relancée pour la même période
        List<TimeSlot> existing = timeSlotRepository.findByLawyerIdAndDate(availability.getLawyerId(), date);

        int created = 0;
        LocalTime cursor = availability.getStartTime();

        while (cursor.plusMinutes(availability.getSlotDurationMinutes()).compareTo(availability.getEndTime()) <= 0) {
            LocalTime slotEnd = cursor.plusMinutes(availability.getSlotDurationMinutes());

            final LocalTime slotStart = cursor; // effectively final pour le lambda
            boolean alreadyExists = existing.stream()
                    .anyMatch(t -> t.getStartTime().equals(slotStart));

            if (!alreadyExists) {
                TimeSlot slot = new TimeSlot();
                slot.setLawyerId(availability.getLawyerId());
                slot.setAvailabilityId(availability.getId());
                slot.setDate(date);
                slot.setStartTime(slotStart);
                slot.setEndTime(slotEnd);
                slot.setStatus(SlotStatus.AVAILABLE);
                timeSlotRepository.save(slot);
                created++;
            }

            cursor = slotEnd;
        }

        return created;
    }

    /**
     * Retourne la première date >= from qui tombe sur le jour de semaine demandé.
     */
    private LocalDate nextOrSameDayOfWeek(LocalDate from, java.time.DayOfWeek targetDay) {
        LocalDate date = from;
        while (date.getDayOfWeek() != targetDay) {
            date = date.plusDays(1);
        }
        return date;
    }

    // ══════════════════════════════════════════════════════════
    //  Validation métier
    // ══════════════════════════════════════════════════════════

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new InvalidAvailabilityException(
                "L'heure de fin doit être après l'heure de début");
        }
    }

    /**
     * Vérifie que la nouvelle plage horaire ne chevauche pas une disponibilité
     * active existante du même avocat sur le même jour de semaine.
     */
    private void checkNoOverlap(Long lawyerId, CreateAvailabilityRequest request) {
        List<Availability> sameDay = availabilityRepository
                .findByLawyerIdAndDayOfWeek(lawyerId, request.getDayOfWeek());

        boolean overlaps = sameDay.stream()
                .filter(Availability::isActive)
                .anyMatch(existing -> timeRangesOverlap(
                        existing.getStartTime(), existing.getEndTime(),
                        request.getStartTime(), request.getEndTime()
                ));

        if (overlaps) {
            throw new InvalidAvailabilityException(
                "Cette plage horaire chevauche une disponibilité existante pour ce jour");
        }
    }

    private boolean timeRangesOverlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}