package juribook.booking_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de création d'une disponibilité récurrente.
 *
 * Appelé par POST /api/lawyers/{lawyerId}/availabilities.
 * La création déclenche immédiatement la génération des TimeSlot
 * concrets sur une fenêtre glissante (par défaut 4 semaines à venir,
 * configurable via generationWeeks).
 */
@Data
public class CreateAvailabilityRequest {

    @NotNull(message = "Le jour de la semaine est obligatoire")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    @NotNull(message = "L'heure de fin est obligatoire")
    private LocalTime endTime;

    @NotNull(message = "La durée d'un créneau est obligatoire")
    @Min(value = 5, message = "La durée minimale d'un créneau est 5 minutes")
    @Max(value = 480, message = "La durée maximale d'un créneau est 480 minutes (8h)")
    private Integer slotDurationMinutes;

    // Date à partir de laquelle la disponibilité s'applique.
    // Si non fournie, s'applique dès aujourd'hui.
    private LocalDate validFrom;

    // Date jusqu'à laquelle la disponibilité s'applique (nullable = indéfini)
    private LocalDate validUntil;

    // Nombre de semaines sur lesquelles générer les créneaux concrets
    // immédiatement après la création (défaut 4 si non fourni)
    @Min(value = 1, message = "Au moins 1 semaine de génération est requise")
    @Max(value = 12, message = "Maximum 12 semaines de génération en une fois")
    private Integer generationWeeks;
}
