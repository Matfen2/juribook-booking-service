package juribook.booking_service.exception;

/**
 * Levée quand un créneau (TimeSlot) n'existe pas en BDD.
 * Transformée en 404 par GlobalExceptionHandler.
 */
public class TimeSlotNotFoundException extends RuntimeException {
    public TimeSlotNotFoundException(String message) {
        super(message);
    }
}
