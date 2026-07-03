package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import juribook.booking_service.dto.response.BookingHistoryResponse;
import juribook.booking_service.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST pour le tableau de bord des réservations d'un avocat.
 *
 * Route :
 *   GET /api/lawyers/{lawyerId}/bookings → LAWYER (toutes ses réservations)
 *
 * ⚠️ Même limite connue que AvailabilityController/TimeSlotController :
 * vérifie uniquement le rôle LAWYER du token, pas que le lawyerId de
 * l'URL correspond à l'avocat authentifié (pas de résolution
 * authUserId → lawyerId sans appel au lawyer-service). N'importe quel
 * compte LAWYER peut donc consulter le tableau de bord d'un autre
 * avocat. À corriger avant la mise en production.
 */
@RestController
@RequestMapping("/api/lawyers/{lawyerId}/bookings")
@RequiredArgsConstructor
@Tag(name = "Réservations", description = "Tableau de bord des réservations d'un avocat")
public class LawyerBookingsController {

    private final BookingService bookingService;

    // ── GET /api/lawyers/{lawyerId}/bookings ──────────────────
    @GetMapping
    @Operation(
        summary = "Tableau de bord des réservations d'un avocat",
        description = """
            Retourne toutes les réservations de l'avocat (tous statuts
            confondus — PENDING, CONFIRMED, CANCELLED, COMPLETED), enrichies
            de la date/heure du créneau, triées du rendez-vous le plus
            proche au plus lointain (file à traiter, pas un journal).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste des réservations (peut être vide)"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (LAWYER requis)")
    })
    public ResponseEntity<List<BookingHistoryResponse>> getLawyerBookings(@PathVariable Long lawyerId) {
        return ResponseEntity.ok(bookingService.getLawyerBookings(lawyerId));
    }
}