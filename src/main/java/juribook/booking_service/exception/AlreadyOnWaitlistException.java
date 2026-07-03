package juribook.booking_service.exception;

public class AlreadyOnWaitlistException extends RuntimeException {
    public AlreadyOnWaitlistException(String message) {
        super(message);
    }
}