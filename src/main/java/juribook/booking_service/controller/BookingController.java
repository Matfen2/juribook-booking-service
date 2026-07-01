package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST pour la réservation de créneaux et leur traitement.
 *
 * Routes :
 *   POST  /api/bookings              → CLIENT (réserver un créneau)
 *   PATCH /api/bookings/{id}/confirm → LAWYER  (accepter une demande PENDING)
 *   PATCH /api/bookings/{id}/reject  → LAWYER  (refuser une demande PENDING)
 *
 * Le clientId (pour la réservation) et le lawyerId (pour confirm/reject,
 * utilisé uniquement pour le log, cf. limite connue dans BookingService)
 * ne sont jamais pris dans le corps de la requête : ils sont extraits du
 * JWT (claim "id", placé en principal par JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Réservations", description = "Réservation de créneaux par les clients, confirmation/refus par les avocats")
public class BookingController {

    private final BookingService bookingService;

    // ── POST /api/bookings ────────────────────────────────────
    @PostMapping
    @Operation(
        summary = "Réserver un créneau",
        description = """
            Crée une réservation en statut PENDING (en attente de réponse de
            l'avocat) et marque immédiatement le créneau comme BOOKED, pour
            qu'il ne soit plus proposé à d'autres clients.

            Protégé contre la double réservation (Sprint 4.3) : si deux
            clients réservent le même créneau au même instant, un seul
            obtient le 201, l'autre reçoit un 409 Conflict.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Réservation créée, créneau marqué BOOKED"),
        @ApiResponse(responseCode = "400", description = "Données invalides ou créneau indisponible (bloqué, passé)"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (CLIENT requis)"),
        @ApiResponse(responseCode = "404", description = "Créneau introuvable"),
        @ApiResponse(responseCode = "409", description = "Créneau déjà réservé (double réservation)")
    })
    public ResponseEntity<BookingResponse> createBooking(
            Authentication authentication,
            @Valid @RequestBody CreateBookingRequest request) {

        Long clientId = (Long) authentication.getPrincipal();
        BookingResponse response = bookingService.createBooking(clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── PATCH /api/bookings/{id}/confirm ──────────────────────
    @PatchMapping("/{id}/confirm")
    @Operation(
        summary = "Confirmer une réservation",
        description = "L'avocat accepte une demande PENDING. Le créneau reste BOOKED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Réservation confirmée"),
        @ApiResponse(responseCode = "400", description = "La réservation n'est pas en statut PENDING"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (LAWYER requis)"),
        @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    public ResponseEntity<BookingResponse> confirmBooking(
            @PathVariable Long id,
            Authentication authentication) {

        Long lawyerAuthUserId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.confirmBooking(lawyerAuthUserId, id));
    }

    // ── PATCH /api/bookings/{id}/reject ───────────────────────
    @PatchMapping("/{id}/reject")
    @Operation(
        summary = "Refuser une réservation",
        description = "L'avocat refuse une demande PENDING. Le créneau est immédiatement libéré (AVAILABLE)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Réservation refusée, créneau libéré"),
        @ApiResponse(responseCode = "400", description = "La réservation n'est pas en statut PENDING"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (LAWYER requis)"),
        @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    public ResponseEntity<BookingResponse> rejectBooking(
            @PathVariable Long id,
            Authentication authentication) {

        Long lawyerAuthUserId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.rejectBooking(lawyerAuthUserId, id));
    }
}