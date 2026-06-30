package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.BookingConflictException;
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
 * Service métier pour la réservation de créneaux par les clients
 * (Sprint 4.2 + 4.3 : double réservation).
 *
 * Protection de la concurrence (Sprint 4.3) : le créneau est lu avec un
 * verrou pessimiste (SELECT ... FOR UPDATE via TimeSlotRepository.findByIdForUpdate).
 * Si deux clients réservent le même créneau au même instant, la seconde
 * transaction attend la fin de la première (commit), puis relit le statut
 * à jour — elle voit BOOKED et se fait rejeter avec un 409 Conflict, au
 * lieu de passer en double comme c'était possible avant ce sprint.
 *
 * Filet de sécurité supplémentaire en base (V5__add_unique_active_booking_per_slot.sql) :
 * un index unique partiel empêche plus d'une réservation active (statut
 * différent de CANCELLED) par créneau, au cas où le verrou applicatif
 * serait contourné.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;

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