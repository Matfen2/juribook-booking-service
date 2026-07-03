package juribook.booking_service.exception;

/**
 * Levée quand une disponibilité n'existe pas en BDD.
 * Transformée en 404 par GlobalExceptionHandler.
 */
public class AvailabilityNotFoundException extends RuntimeException {
    public AvailabilityNotFoundException(String message) {
        super(message);
    }
}
