package juribook.booking_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de réservation d'un créneau par un client.
 *
 * Appelé par POST /api/bookings. Le clientId n'est volontairement pas
 * un champ du DTO : il est extrait du JWT (claim "id") côté controller,
 * un client ne peut réserver que pour lui-même.
 */
@Data
public class CreateBookingRequest {

    @NotNull(message = "Le créneau (timeSlotId) est obligatoire")
    private Long timeSlotId;

    @NotBlank(message = "Le motif de consultation est obligatoire")
    @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères")
    private String reason;
}