package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juribook.booking_service.dto.request.CreateAvailabilityRequest;
import juribook.booking_service.dto.response.AvailabilityResponse;
import juribook.booking_service.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST pour les disponibilités récurrentes des avocats.
 *
 * Routes :
 *   POST   /api/lawyers/{lawyerId}/availabilities  → LAWYER (créer + générer les créneaux)
 *   GET    /api/lawyers/{lawyerId}/availabilities  → public (consulter)
 *   DELETE /api/lawyers/{lawyerId}/availabilities/{id} → LAWYER (désactiver)
 *
 * ⚠️ Limite connue (à lever en Sprint 3.x suivant) : ce controller vérifie
 * uniquement que l'utilisateur authentifié a le rôle LAWYER (via SecurityConfig),
 * mais ne vérifie PAS que le lawyerId du path correspond bien à l'avocat
 * authentifié, cette correspondance nécessiterait un appel inter-services
 * vers le lawyer-service (GET /api/lawyers/profile) pour résoudre authUserId
 * → lawyerId. Pour l'instant, n'importe quel utilisateur avec le rôle LAWYER
 * peut déclarer des disponibilités pour n'importe quel lawyerId. À corriger
 * avant mise en production (ex: lawyer-service expose un endpoint interne
 * de vérification, ou le JWT embarque directement le lawyerId).
 */
@RestController
@RequestMapping("/api/lawyers/{lawyerId}/availabilities")
@RequiredArgsConstructor
@Tag(name = "Disponibilités", description = "Gestion des disponibilités récurrentes des avocats")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    // ── POST /api/lawyers/{lawyerId}/availabilities ──────────
    @PostMapping
    @Operation(
        summary = "Déclarer une disponibilité récurrente",
        description = """
            Crée une disponibilité hebdomadaire (ex: tous les lundis 9h-17h)
            et génère immédiatement les créneaux concrets (TimeSlot) sur les
            prochaines semaines (4 par défaut, configurable via generationWeeks).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Disponibilité créée et créneaux générés"),
        @ApiResponse(responseCode = "400", description = "Données invalides ou chevauchement avec une disponibilité existante"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (LAWYER requis)")
    })
    public ResponseEntity<AvailabilityResponse> createAvailability(
            @Parameter(description = "Id technique de l'avocat (entité Lawyer du lawyer-service)")
            @PathVariable Long lawyerId,
            @Valid @RequestBody CreateAvailabilityRequest request) {

        AvailabilityResponse response = availabilityService.createAvailability(lawyerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/lawyers/{lawyerId}/availabilities ───────────
    @GetMapping
    @Operation(
        summary = "Lister les disponibilités d'un avocat",
        description = "Accessible à tous — pas de JWT requis. Retourne actives et inactives."
    )
    @ApiResponse(responseCode = "200", description = "Liste des disponibilités")
    public ResponseEntity<List<AvailabilityResponse>> getAvailabilities(
            @PathVariable Long lawyerId) {
        return ResponseEntity.ok(availabilityService.getByLawyerId(lawyerId));
    }

    // ── DELETE /api/lawyers/{lawyerId}/availabilities/{id} ───
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Désactiver une disponibilité",
        description = "Ne supprime pas les créneaux déjà générés, empêche seulement la génération future."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Disponibilité désactivée"),
        @ApiResponse(responseCode = "400", description = "La disponibilité n'appartient pas à cet avocat"),
        @ApiResponse(responseCode = "404", description = "Disponibilité introuvable")
    })
    public ResponseEntity<AvailabilityResponse> deactivate(
            @PathVariable Long lawyerId,
            @PathVariable Long id) {
        return ResponseEntity.ok(availabilityService.deactivate(lawyerId, id));
    }
}
