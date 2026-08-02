package juribook.booking_service.dto.response;

import juribook.booking_service.entity.BookingDocument;

import java.time.LocalDateTime;

public record DocumentUploadResponse(
    Long id,
    Long bookingId,
    String filename,
    String contentType,
    long sizeBytes,
    String status,
    LocalDateTime uploadedAt
) {
    public static DocumentUploadResponse from(BookingDocument doc) {
        return new DocumentUploadResponse(
                doc.getId(), doc.getBookingId(), doc.getOriginalFilename(),
                doc.getContentType(), doc.getSizeBytes(), doc.getStatus().name(),
                doc.getUploadedAt()
        );
    }
}