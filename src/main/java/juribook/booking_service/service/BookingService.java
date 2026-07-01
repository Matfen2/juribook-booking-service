package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.BookingConflictException;
import juribook.booking_service.exception.BookingNotFoundException;
import juribook.booking_service.exception.InvalidBookingException;
import juribook.booking_service.exception.InvalidTimeSlotException;
import juribook.booking_service.exception.TimeSlotNotFoundException;
import juribook.booking_service.repository.BookingRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service métier pour la réservation de créneaux par les clients et leur
 * traitement par les avocats.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;

    // ══════════════════════════════════════════════════════════
    //  Réservation par le client
    // ══════════════════════════════════════════════════════════
    @Transactional
    public BookingResponse createBooking(Long clientId, CreateBookingRequest request) {
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(request.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable : id=" + request.getTimeSlotId()));

        validateSlotIsBookable(slot);

        Booking booking = new Booking();
        booking.setClientId(clientId);
        booking.setLawyerId(slot.getLawyerId());
        booking.setTimeSlotId(slot.getId());
        booking.setReason(request.getReason());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved;
        try {
            saved = bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            // Filet de sécurité si l'index unique partiel (V5) est déclenché
            // malgré le verrou pessimiste, ne devrait normalement jamais
            // arriver, mais protège contre un contournement du verrou.
            throw new BookingConflictException(
                "Ce créneau vient d'être réservé par quelqu'un d'autre");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookingId(saved.getId());
        timeSlotRepository.save(slot);

        log.info("Réservation créée : bookingId={}, clientId={}, lawyerId={}, timeSlotId={}",
                saved.getId(), clientId, slot.getLawyerId(), slot.getId());

        return BookingResponse.from(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  Confirmation / refus par l'avocat
    // ══════════════════════════════════════════════════════════
    /**
     * L'avocat confirme une demande de réservation en attente.
     * Le créneau reste BOOKED (aucun changement côté TimeSlot).
     */
    @Transactional
    public BookingResponse confirmBooking(Long lawyerId, Long bookingId) {
        Booking booking = getPendingBookingOrThrow(bookingId);

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        log.info("Réservation confirmée : bookingId={}, lawyerId={}", bookingId, lawyerId);
        return BookingResponse.from(saved);
    }

    /**
     * L'avocat refuse une demande de réservation en attente.
     * Le Booking passe en CANCELLED et le créneau est immédiatement
     * libéré (retour à AVAILABLE) pour redevenir réservable par un
     * autre client.
     */
    @Transactional
    public BookingResponse rejectBooking(Long lawyerId, Long bookingId) {
        Booking booking = getPendingBookingOrThrow(bookingId);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable pour cette réservation : id=" + booking.getTimeSlotId()));
        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setBookingId(null);
        timeSlotRepository.save(slot);

        log.info("Réservation refusée : bookingId={}, lawyerId={}, timeSlotId={} libéré",
                bookingId, lawyerId, slot.getId());
        return BookingResponse.from(savedBooking);
    }

    private Booking getPendingBookingOrThrow(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(
                    "Réservation introuvable : id=" + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingException(
                "Seule une réservation PENDING peut être confirmée ou refusée (statut actuel : "
                    + booking.getStatus() + ")");
        }
        return booking;
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers privés
    // ══════════════════════════════════════════════════════════
    private void validateSlotIsBookable(TimeSlot slot) {
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BookingConflictException("Ce créneau est déjà réservé");
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new InvalidTimeSlotException(
                "Ce créneau n'est plus disponible à la réservation");
        }
        if (!slot.isBookable()) {
            throw new InvalidTimeSlotException(
                "Impossible de réserver un créneau déjà passé");
        }
    }
}