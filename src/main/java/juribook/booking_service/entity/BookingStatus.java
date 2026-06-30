package juribook.booking_service.entity;

/**
 * Statut d'une réservation (Booking) dans son cycle de vie.
 *
 * Transitions valides :
 *   PENDING    → CONFIRMED   (l'avocat accepte la demande)
 *   PENDING    → CANCELLED   (annulation avant confirmation)
 *   CONFIRMED  → CANCELLED   (annulation après confirmation, règle des 24h)
 *   CONFIRMED  → COMPLETED   (rendez-vous honoré, créneau passé)
 *
 * Pas de transition retour : une fois CANCELLED ou COMPLETED, une réservation
 * est terminale (contrairement à TimeSlot.status qui peut redevenir AVAILABLE).
 */
public enum BookingStatus {
    PENDING,     // demande créée par le client, en attente de réponse de l'avocat
    CONFIRMED,   // l'avocat a accepté la demande
    COMPLETED,   // le rendez-vous a eu lieu
    CANCELLED    // annulée par le client ou l'avocat, à n'importe quel stade avant COMPLETED
}