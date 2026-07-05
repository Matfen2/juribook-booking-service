package juribook.booking_service.exception;

/** 400 - fichier vide, trop volumineux, type non autorisé, ou réservation dans un statut incompatible. */
public class DocumentUploadException extends RuntimeException {
    public DocumentUploadException(String message) {
        super(message);
    }
}