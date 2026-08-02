package juribook.booking_service.event;

import juribook.booking_service.entity.BookingDocument;

public interface DocumentEventPublisher {

    void publishDocumentUploaded(BookingDocument document);

    /** Publié après scan + déplacement vers le stockage permanent. */
    void publishDocumentReady(BookingDocument document);
}