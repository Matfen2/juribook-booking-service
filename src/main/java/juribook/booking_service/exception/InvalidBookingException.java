package juribook.booking_service.exception;

/**
 * Levée quand une action est demandée sur un Booking dans un statut qui
 * ne le permet pas, ex : confirmer/refuser une réservation déjà
 * CONFIRMED, CANCELLED ou COMPLETED (seule PENDING peut l'être).
 */
public class InvalidBookingException extends RuntimeException {
    public InvalidBookingException(String message) {
        super(message);
    }
}