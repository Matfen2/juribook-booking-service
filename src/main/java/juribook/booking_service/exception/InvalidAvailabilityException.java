package juribook.booking_service.exception;

/**
 * Levée quand les données d'une disponibilité sont invalides au sens métier
 * (chevauchement avec une disponibilité existante, plage horaire incohérente
 * non détectée par la validation Bean Validation, etc.)
 * Transformée en 400 par GlobalExceptionHandler.
 */
public class InvalidAvailabilityException extends RuntimeException {
    public InvalidAvailabilityException(String message) {
        super(message);
    }
}
