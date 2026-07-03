package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import juribook.booking_service.dto.response.WaitlistEntryResponse;
import juribook.booking_service.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST pour la liste d'attente d'un avocat complet.
 *
 * Routes :
 *   POST /api/waitlist/{lawyerId} → CLIENT (s'inscrire)
 *   GET  /api/waitlist/{lawyerId} → public (consulter, utilisé en
 *        interne par le notification-service, pour
 *        résoudre les clients à notifier suite à un slot.released)
 *
 * Le clientId n'est jamais pris dans le corps de la requête : il est
 * extrait du JWT (claim "id", placé en principal par
 * JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
@Tag(name = "Liste d'attente", description = "Inscription des clients sur la liste d'attente d'un avocat")
public class WaitlistController {

    private final WaitlistService waitlistService;

    // ── POST /api/waitlist/{lawyerId} ─────────────────────────
    @PostMapping("/{lawyerId}")
    @Operation(
        summary = "S'inscrire sur la liste d'attente d'un avocat",
        description = "Le client s'inscrit pour être notifié si un créneau se libère chez cet avocat."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Inscription enregistrée"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (CLIENT requis)"),
        @ApiResponse(responseCode = "409", description = "Déjà inscrit sur la liste d'attente de cet avocat")
    })
    public ResponseEntity<WaitlistEntryResponse> joinWaitlist(
            @PathVariable Long lawyerId,
            Authentication authentication) {

        Long clientId = (Long) authentication.getPrincipal();
        WaitlistEntryResponse response = waitlistService.joinWaitlist(lawyerId, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/waitlist/{lawyerId} ──────────────────────────
    @GetMapping("/{lawyerId}")
    @Operation(
        summary = "Lister les clients en attente pour un avocat",
        description = """
            Accessible à tous — pas de JWT requis. Consommé principalement
            par le notification-service pour résoudre les clients à
            notifier quand un créneau se libère (Sprint 4.9).
            """
    )
    @ApiResponse(responseCode = "200", description = "Liste des inscriptions, triée par ordre d'inscription")
    public ResponseEntity<List<WaitlistEntryResponse>> getWaitlist(@PathVariable Long lawyerId) {
        return ResponseEntity.ok(waitlistService.getWaitlist(lawyerId));
    }
}