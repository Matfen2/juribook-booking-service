package juribook.booking_service.exception;

/**
 * Levée quand un client tente de réserver un créneau déjà pris
 * (status BOOKED, ou collision détectée par le filet de sécurité BDD).
 *
 * Distincte de InvalidTimeSlotException (qui couvre les autres cas
 * d'indisponibilité : créneau passé, bloqué, etc. → 400) car la
 * sémantique HTTP est différente : ici, la requête était valide au
 * moment de l'envoi, mais l'état de la ressource a changé entre-temps
 * (race condition) → 409 Conflict, pas 400 Bad Request.
 */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}