package juribook.booking_service.exception;

/**
 * Levée pour toute opération invalide sur un créneau :
 *   - création d'un créneau qui chevauche un créneau existant
 *   - suppression d'un créneau déjà réservé (BOOKED) ou passé (COMPLETED)
 *   - déblocage d'un créneau qui n'est pas BLOCKED
 *   - créneau dans le passé
 * Transformée en 400 par GlobalExceptionHandler.
 */
public class InvalidTimeSlotException extends RuntimeException {
    public InvalidTimeSlotException(String message) {
        super(message);
    }
}
