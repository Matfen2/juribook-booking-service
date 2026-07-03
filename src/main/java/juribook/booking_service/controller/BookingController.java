package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingHistoryResponse;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST pour la réservation de créneaux et leur traitement.
 *
 * Routes :
 *   POST  /api/bookings              → CLIENT          (réserver un créneau)
 *   GET   /api/bookings              → CLIENT          (historique de ses réservations)
 *   GET   /api/bookings/{id}         → public           (détail enrichi, résolution inter-services)
 *   PATCH /api/bookings/{id}/confirm → LAWYER           (accepter une demande PENDING)
 *   PATCH /api/bookings/{id}/reject  → LAWYER           (refuser une demande PENDING)
 *   PATCH /api/bookings/{id}/cancel  → CLIENT ou LAWYER (annuler une réservation CONFIRMED, règle des 24h)
 *
 * Le tableau de bord avocat (GET /api/lawyers/{lawyerId}/bookings) est
 * exposé par LawyerBookingsController, pas ici.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Réservations", description = "Réservation, consultation, confirmation, refus et annulation de créneaux")
public class BookingController {

    private final BookingService bookingService;

    // ── POST /api/bookings ────────────────────────────────────
    @PostMapping
    @Operation(summary = "Réserver un créneau")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Réservation créée, créneau marqué BOOKED"),
        @ApiResponse(responseCode = "400", description = "Données invalides ou créneau indisponible"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (CLIENT requis)"),
        @ApiResponse(responseCode = "404", description = "Créneau introuvable"),
        @ApiResponse(responseCode = "409", description = "Créneau déjà réservé")
    })
    public ResponseEntity<BookingResponse> createBooking(
            Authentication authentication,
            @Valid @RequestBody CreateBookingRequest request) {

        Long clientId = (Long) authentication.getPrincipal();
        BookingResponse response = bookingService.createBooking(clientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/bookings ──────────────────────────────────────
    @GetMapping
    @Operation(summary = "Historique de mes réservations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste des réservations (peut être vide)"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (CLIENT requis)")
    })
    public ResponseEntity<List<BookingHistoryResponse>> getMyBookings(Authentication authentication) {
        Long clientId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getMyBookings(clientId));
    }

    // ── GET /api/bookings/{id} ─────────────────────────────────
    @GetMapping("/{id}")
    @Operation(
        summary = "Détail enrichi d'une réservation",
        description = """
            Public — pas de JWT requis. Utilisé en interne par les autres
            microservices pour résoudre la date/heure d'un rendez-vous à partir d'un bookingId reçu via
            un événement Kafka (qui ne transporte que timeSlotId, pas la
            date/heure résolue).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Détail retourné"),
        @ApiResponse(responseCode = "404", description = "Réservation introuvable")
    })
    public ResponseEntity<BookingHistoryResponse> getBookingDetails(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingDetails(id));
    }

    // ── PATCH /api/bookings/{id}/confirm ──────────────────────
    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirmer une réservation")
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
    @Operation(summary = "Refuser une réservation")
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

    // ── PATCH /api/bookings/{id}/cancel ───────────────────────
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Annuler une réservation confirmée")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Réservation annulée, créneau libéré"),
        @ApiResponse(responseCode = "400", description = "Statut invalide ou moins de 24h avant le rendez-vous"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Cette réservation n'appartient pas à ce client"),
        @ApiResponse(responseCode = "404", description = "Réservation ou créneau introuvable")
    })
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            Authentication authentication) {

        Long actorId = (Long) authentication.getPrincipal();
        String actorRole = extractRole(authentication);
        return ResponseEntity.ok(bookingService.cancelBooking(actorId, actorRole, id));
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse("");
    }
}