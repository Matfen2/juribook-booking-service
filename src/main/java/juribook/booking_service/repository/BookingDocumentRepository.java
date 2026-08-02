package juribook.booking_service.repository;

import juribook.booking_service.entity.BookingDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingDocumentRepository extends JpaRepository<BookingDocument, Long> {

    List<BookingDocument> findByBookingId(Long bookingId);
}