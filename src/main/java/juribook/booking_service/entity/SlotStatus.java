package juribook.booking_service.entity;

/**
 * Statut d'un créneau (TimeSlot) dans son cycle de vie.
 *
 * Transitions valides :
 *   AVAILABLE  → BOOKED     (un client réserve)
 *   AVAILABLE  → BLOCKED    (l'avocat bloque le créneau manuellement)
 *   BOOKED     → CANCELLED  (annulation côté client ou avocat)
 *   BOOKED     → COMPLETED  (rendez-vous honoré, passé)
 *   CANCELLED  → AVAILABLE  (le créneau redevient réservable après annulation)
 *   BLOCKED    → AVAILABLE  (l'avocat débloque le créneau)
 */
public enum SlotStatus {
    AVAILABLE,   // libre, réservable par un client
    BOOKED,      // réservé par un client (lié à une réservation : Sprint 3.2)
    BLOCKED,     // bloqué manuellement par l'avocat (indisponibilité ponctuelle)
    CANCELLED,   // une réservation a été annulée sur ce créneau
    COMPLETED    // le rendez-vous a eu lieu, créneau passé et honoré
}