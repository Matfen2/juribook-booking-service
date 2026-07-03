package juribook.booking_service.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO pour bloquer une période de congés ou d'indisponibilité.
 *
 * Appelé par POST /api/lawyers/{lawyerId}/slots/block.
 * Marque tous les créneaux AVAILABLE de l'avocat sur [fromDate, toDate]
 * comme BLOCKED. Les créneaux déjà BOOKED ne sont jamais affectés,
 * un congé déclaré après coup ne peut pas annuler silencieusement une
 * réservation existante (l'avocat doit la gérer explicitement ailleurs).
 */
@Data
public class BlockPeriodRequest {

    @NotNull(message = "La date de début est obligatoire")
    @FutureOrPresent(message = "La date de début ne peut pas être dans le passé")
    private LocalDate fromDate;

    @NotNull(message = "La date de fin est obligatoire")
    private LocalDate toDate;

    @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères")
    private String reason; // ex: "Congés", "Formation", "Indisponibilité médicale"
}
