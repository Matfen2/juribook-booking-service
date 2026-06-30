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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST pour la réservation de créneaux par les clients.
 *
 * Route :
 *   POST /api/bookings → CLIENT (réserver un créneau)
 *
 * Le clientId n'est jamais pris dans le corps de la requête : il est
 * extrait du JWT (claim "id", placé en principal par JwtAuthenticationFilter)
 * via l'objet Authentication, un client ne peut donc réserver que pour
 * lui-même.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Réservations", description = "Réservation de créneaux par les clients")
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
}