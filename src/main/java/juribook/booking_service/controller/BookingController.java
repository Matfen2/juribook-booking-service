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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST pour la réservation de créneaux et leur traitement.
 *
 * Routes :
 *   POST  /api/bookings              → CLIENT          (réserver un créneau)
 *   GET   /api/bookings              → CLIENT          (historique de ses réservations)
 *   PATCH /api/bookings/{id}/confirm → LAWYER           (accepter une demande PENDING)
 *   PATCH /api/bookings/{id}/reject  → LAWYER           (refuser une demande PENDING)
 *   PATCH /api/bookings/{id}/cancel  → CLIENT ou LAWYER (annuler une réservation CONFIRMED, règle des 24h)
 *
 * Le tableau de bord avocat (GET /api/lawyers/{lawyerId}/bookings)
 * est exposé par LawyerBookingsController, pas ici : @RequestMapping de
 * classe empêcherait de monter un préfixe /api/lawyers/... différent
 * dans ce controller-ci, et ça reste cohérent avec le regroupement déjà
 * utilisé par AvailabilityController/TimeSlotController.
 *
 * L'identité de l'appelant (clientId, ou actorId + actorRole pour
 * confirm/reject/cancel) n'est jamais prise dans le corps de la requête :
 * elle est extraite du JWT (claims "id" et "role", placés en principal
 * et en authority par JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Réservations", description = "Réservation, consultation, confirmation, refus et annulation de créneaux")
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

            Protégé contre la double réservation : si deux
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

    // ── GET /api/bookings ──────────────────────────────────────
    @GetMapping
    @Operation(
        summary = "Historique de mes réservations",
        description = """
            Retourne toutes les réservations du client authentifié (tous
            statuts confondus), enrichies de la date/heure du créneau,
            triées du rendez-vous le plus récent au plus ancien.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste des réservations (peut être vide)"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (CLIENT requis)")
    })
    public ResponseEntity<List<BookingHistoryResponse>> getMyBookings(Authentication authentication) {
        Long clientId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(bookingService.getMyBookings(clientId));
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

    // ── PATCH /api/bookings/{id}/cancel ───────────────────────
    @PatchMapping("/{id}/cancel")
    @Operation(
        summary = "Annuler une réservation confirmée",
        description = """
            Le client ou l'avocat annule une réservation CONFIRMED. Refusée
            avec un message explicite si le rendez-vous a lieu dans moins
            de 24h. Le créneau est immédiatement libéré (AVAILABLE).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Réservation annulée, créneau libéré"),
        @ApiResponse(responseCode = "400", description = "Statut invalide (pas CONFIRMED) ou moins de 24h avant le rendez-vous"),
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

    // ── Helper ─────────────────────────────────────────────────
    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .orElse("");
    }
}