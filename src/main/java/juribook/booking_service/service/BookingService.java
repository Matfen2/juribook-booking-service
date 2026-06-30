package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.InvalidTimeSlotException;
import juribook.booking_service.exception.TimeSlotNotFoundException;
import juribook.booking_service.repository.BookingRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service métier pour la réservation de créneaux par les clients (Sprint 4.2).
 *
 * Règle centrale : réserver un créneau crée un Booking en statut PENDING
 * (en attente de réponse de l'avocat, cf. BookingStatus) ET marque
 * immédiatement le TimeSlot associé comme BOOKED, pour qu'il ne soit plus
 * proposé à d'autres clients pendant que l'avocat traite la demande.
 *
 * TimeSlot et Booking vivent dans la même base (booking-service), l'accès
 * direct via TimeSlotRepository ne nécessite donc pas d'appel inter-services.
 *
 * ⚠️ Protection de la concurrence (deux clients réservent le même créneau
 * au même instant) : la vérification de statut ci-dessous n'est pas encore
 * protégée par une contrainte BDD ou un verrou, c'est l'objet du Sprint 4.3
 * (double réservation → 409 Conflict).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Transactional
    public BookingResponse createBooking(Long clientId, CreateBookingRequest request) {
        TimeSlot slot = timeSlotRepository.findById(request.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable : id=" + request.getTimeSlotId()));

        validateSlotIsBookable(slot);

        Booking booking = new Booking();
        booking.setClientId(clientId);
        booking.setLawyerId(slot.getLawyerId());
        booking.setTimeSlotId(slot.getId());
        booking.setReason(request.getReason());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved = bookingRepository.save(booking);

        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookingId(saved.getId());
        timeSlotRepository.save(slot);

        log.info("Réservation créée : bookingId={}, clientId={}, lawyerId={}, timeSlotId={}",
                saved.getId(), clientId, slot.getLawyerId(), slot.getId());

        return BookingResponse.from(saved);
    }

    private void validateSlotIsBookable(TimeSlot slot) {
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