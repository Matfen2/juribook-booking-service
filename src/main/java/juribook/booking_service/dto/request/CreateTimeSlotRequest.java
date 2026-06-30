package juribook.booking_service.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de création d'un créneau ponctuel, hors récurrence.
 *
 * Appelé par POST /api/lawyers/{lawyerId}/slots — utilisé par exemple
 * pour ajouter une disponibilité exceptionnelle un samedi, ou un créneau
 * supplémentaire en dehors des plages habituelles de l'avocat.
 *
 * availabilityId reste null pour ce type de créneau (champ géré côté
 * entité, pas exposé ici), il se distingue ainsi des créneaux générés
 * automatiquement par AvailabilityService.
 */
@Data
public class CreateTimeSlotRequest {

    @NotNull(message = "La date est obligatoire")
    @FutureOrPresent(message = "La date ne peut pas être dans le passé")
    private LocalDate date;

    @NotNull(message = "L'heure de début est obligatoire")
    private LocalTime startTime;

    @NotNull(message = "L'heure de fin est obligatoire")
    private LocalTime endTime;
}
