package juribook.booking_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import juribook.booking_service.dto.request.BlockPeriodRequest;
import juribook.booking_service.dto.request.CreateTimeSlotRequest;
import juribook.booking_service.dto.response.BlockPeriodResponse;
import juribook.booking_service.dto.response.TimeSlotResponse;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller REST pour la gestion ponctuelle des créneaux (Sprint 3.3).
 *
 * Routes :
 *   POST   /api/lawyers/{lawyerId}/slots              → LAWYER (créneau ponctuel)
 *   DELETE /api/lawyers/{lawyerId}/slots/{id}          → LAWYER (suppression)
 *   POST   /api/lawyers/{lawyerId}/slots/block          → LAWYER (bloquer une période — congés)
 *   POST   /api/lawyers/{lawyerId}/slots/{id}/unblock   → LAWYER (débloquer un créneau)
 *   GET    /api/lawyers/{lawyerId}/slots                → public (consultation filtrée)
 *
 * ⚠️ Même limite que AvailabilityController : pas de vérification que
 * lawyerId correspond à l'avocat authentifié (cf. note dans ce controller).
 */
@RestController
@RequestMapping("/api/lawyers/{lawyerId}/slots")
@RequiredArgsConstructor
@Tag(name = "Créneaux", description = "Gestion ponctuelle des créneaux — ajout, suppression, congés")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    // ── POST /api/lawyers/{lawyerId}/slots - créneau ponctuel ──
    @PostMapping
    @Operation(
        summary = "Ajouter un créneau ponctuel",
        description = "Créneau exceptionnel hors récurrence (ex: disponibilité ajoutée un samedi)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Créneau créé"),
        @ApiResponse(responseCode = "400", description = "Données invalides, créneau passé, ou chevauchement"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (LAWYER requis)")
    })
    public ResponseEntity<TimeSlotResponse> createTimeSlot(
            @PathVariable Long lawyerId,
            @Valid @RequestBody CreateTimeSlotRequest request) {

        TimeSlotResponse response = timeSlotService.createTimeSlot(lawyerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── DELETE /api/lawyers/{lawyerId}/slots/{id} ──────────────
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Supprimer un créneau",
        description = "Impossible si le créneau est réservé (BOOKED) ou déjà honoré (COMPLETED)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Créneau supprimé"),
        @ApiResponse(responseCode = "400", description = "Créneau réservé ou déjà honoré"),
        @ApiResponse(responseCode = "404", description = "Créneau introuvable")
    })
    public ResponseEntity<Void> deleteTimeSlot(
            @PathVariable Long lawyerId,
            @PathVariable Long id) {
        timeSlotService.deleteTimeSlot(lawyerId, id);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/lawyers/{lawyerId}/slots/block - congés ──────
    @PostMapping("/block")
    @Operation(
        summary = "Bloquer une période (congés, indisponibilité)",
        description = """
            Marque tous les créneaux AVAILABLE de l'avocat sur [fromDate, toDate]
            comme BLOCKED. Les créneaux déjà réservés (BOOKED) ne sont jamais
            affectés — gérer leur annulation séparément si nécessaire.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Période bloquée, liste des créneaux affectés"),
        @ApiResponse(responseCode = "400", description = "Plage de dates invalide")
    })
    public ResponseEntity<BlockPeriodResponse> blockPeriod(
            @PathVariable Long lawyerId,
            @Valid @RequestBody BlockPeriodRequest request) {
        return ResponseEntity.ok(timeSlotService.blockPeriod(lawyerId, request));
    }

    // ── POST /api/lawyers/{lawyerId}/slots/{id}/unblock ────────
    @PostMapping("/{id}/unblock")
    @Operation(
        summary = "Débloquer un créneau",
        description = "Remet un créneau BLOCKED en AVAILABLE. Échoue si le créneau n'est pas BLOCKED."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Créneau débloqué"),
        @ApiResponse(responseCode = "400", description = "Le créneau n'est pas bloqué"),
        @ApiResponse(responseCode = "404", description = "Créneau introuvable")
    })
    public ResponseEntity<TimeSlotResponse> unblockSlot(
            @PathVariable Long lawyerId,
            @PathVariable Long id) {
        return ResponseEntity.ok(timeSlotService.unblockSlot(lawyerId, id));
    }

    // ── GET /api/lawyers/{lawyerId}/slots - consultation ───────
    @GetMapping
    @Operation(
        summary = "Lister les créneaux d'un avocat",
        description = """
            Accessible à tous — pas de JWT requis.
            Filtres optionnels : plage de dates (défaut : aujourd'hui → +1 mois)
            et statut (AVAILABLE, BOOKED, BLOCKED, CANCELLED, COMPLETED).
            """
    )
    @ApiResponse(responseCode = "200", description = "Liste des créneaux")
    public ResponseEntity<List<TimeSlotResponse>> getSlots(
            @PathVariable Long lawyerId,

            @Parameter(description = "Date de début (incluse), défaut : aujourd'hui")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "Date de fin (incluse), défaut : aujourd'hui + 1 mois")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Filtrer par statut (optionnel)")
            @RequestParam(required = false) SlotStatus status) {

        return ResponseEntity.ok(timeSlotService.getSlots(lawyerId, fromDate, toDate, status));
    }
}
