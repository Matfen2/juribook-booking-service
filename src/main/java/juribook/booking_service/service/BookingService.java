package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingHistoryResponse;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.event.BookingEventPublisher;
import juribook.booking_service.event.SlotEventPublisher;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service métier pour la réservation de créneaux par les clients, leur
 * traitement par les avocats, leur annulation, l'historique client, le
 * tableau de bord avocat, la résolution inter-services d'une réservation,
 * et la publication des événements associés sur Kafka.
 *
 * ⚠️ Limite connue : confirmBooking, rejectBooking, cancelBooking (côté
 * avocat) et getLawyerBookings ne vérifient PAS que le Booking/lawyerId
 * appartient bien à l'avocat authentifié (pas de résolution
 * authUserId → lawyerId sans appel au lawyer-service). Côté client en
 * revanche, l'appartenance EST vérifiée pour cancelBooking et
 * getMyBookings, car Booking.clientId est directement l'authUserId
 * extrait du JWT.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final int CANCELLATION_DEADLINE_HOURS = 24;
    private static final DateTimeFormatter DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final SlotEventPublisher slotEventPublisher;

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
            throw new BookingConflictException(
                "Ce créneau vient d'être réservé par quelqu'un d'autre");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slot.setBookingId(saved.getId());
        timeSlotRepository.save(slot);

        log.info("Réservation créée : bookingId={}, clientId={}, lawyerId={}, timeSlotId={}",
                saved.getId(), clientId, slot.getLawyerId(), slot.getId());

        bookingEventPublisher.publishBookingCreated(saved);

        return BookingResponse.from(saved);
    }

    // ══════════════════════════════════════════════════════════
    //  Confirmation / refus par l'avocat
    // ══════════════════════════════════════════════════════════
    @Transactional
    public BookingResponse confirmBooking(Long lawyerId, Long bookingId) {
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.PENDING);

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        log.info("Réservation confirmée : bookingId={}, lawyerId={}", bookingId, lawyerId);
        bookingEventPublisher.publishBookingConfirmed(saved);

        return BookingResponse.from(saved);
    }

    @Transactional
    public BookingResponse rejectBooking(Long lawyerId, Long bookingId) {
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.PENDING);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        releaseSlot(booking);

        log.info("Réservation refusée : bookingId={}, lawyerId={}, timeSlotId={} libéré",
                bookingId, lawyerId, booking.getTimeSlotId());
        bookingEventPublisher.publishBookingCancelled(savedBooking);
        slotEventPublisher.publishSlotReleased(booking.getLawyerId(), booking.getTimeSlotId());

        return BookingResponse.from(savedBooking);
    }

    // ══════════════════════════════════════════════════════════
    //  Annulation — client ou avocat, règle des 24h
    // ══════════════════════════════════════════════════════════
    @Transactional
    public BookingResponse cancelBooking(Long actorId, String actorRole, Long bookingId) {
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.CONFIRMED);

        if ("CLIENT".equals(actorRole) && !booking.getClientId().equals(actorId)) {
            throw new AccessDeniedException("Cette réservation n'appartient pas à ce client");
        }

        TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable pour cette réservation : id=" + booking.getTimeSlotId()));

        validateCancellationDeadline(slot);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setBookingId(null);
        timeSlotRepository.save(slot);

        log.info("Réservation annulée : bookingId={}, actorId={}, actorRole={}, timeSlotId={} libéré",
                bookingId, actorId, actorRole, slot.getId());
        bookingEventPublisher.publishBookingCancelled(savedBooking);
        slotEventPublisher.publishSlotReleased(booking.getLawyerId(), booking.getTimeSlotId());

        return BookingResponse.from(savedBooking);
    }

    // ══════════════════════════════════════════════════════════
    //  Historique client / Tableau de bord avocat
    // ══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<BookingHistoryResponse> getMyBookings(Long clientId) {
        return enrichAndSort(bookingRepository.findByClientId(clientId), true);
    }

    @Transactional(readOnly = true)
    public List<BookingHistoryResponse> getLawyerBookings(Long lawyerId) {
        return enrichAndSort(bookingRepository.findByLawyerId(lawyerId), false);
    }

    // ══════════════════════════════════════════════════════════
    //  Résolution inter-services d'une réservation 
    // ══════════════════════════════════════════════════════════
    /**
     * Détail enrichi (date/heure du créneau) d'une réservation par id,
     * indépendamment du client ou de l'avocat propriétaire.
     *
     * Endpoint public (cf. GlobalExceptionHandler/SecurityConfig),
     * consommé par le notification-service pour composer l'email de
     * confirmation, qui n'a que bookingId + lawyerId +
     * clientId depuis l'événement Kafka, pas la date/heure du rendez-vous.
     */
    @Transactional(readOnly = true)
    public BookingHistoryResponse getBookingDetails(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(
                    "Réservation introuvable : id=" + bookingId));

        TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId()).orElse(null);
        return BookingHistoryResponse.from(booking, slot);
    }

    private List<BookingHistoryResponse> enrichAndSort(List<Booking> bookings, boolean mostRecentFirst) {
        if (bookings.isEmpty()) {
            return List.of();
        }

        List<Long> timeSlotIds = bookings.stream().map(Booking::getTimeSlotId).toList();
        Map<Long, TimeSlot> slotsById = timeSlotRepository.findAllById(timeSlotIds).stream()
                .collect(Collectors.toMap(TimeSlot::getId, Function.identity()));

        Comparator<BookingHistoryResponse> byAppointmentDateTime = Comparator
                .comparing((BookingHistoryResponse r) -> r.getDate() != null ? r.getDate() : java.time.LocalDate.MIN)
                .thenComparing(r -> r.getStartTime() != null ? r.getStartTime() : java.time.LocalTime.MIN);

        return bookings.stream()
                .map(booking -> BookingHistoryResponse.from(booking, slotsById.get(booking.getTimeSlotId())))
                .sorted(mostRecentFirst ? byAppointmentDateTime.reversed() : byAppointmentDateTime)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers privés
    // ══════════════════════════════════════════════════════════
    private Booking getBookingInStatusOrThrow(Long bookingId, BookingStatus expectedStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(
                    "Réservation introuvable : id=" + bookingId));

        if (booking.getStatus() != expectedStatus) {
            throw new InvalidBookingException(
                "Cette action nécessite une réservation " + expectedStatus
                    + " (statut actuel : " + booking.getStatus() + ")");
        }
        return booking;
    }

    private void releaseSlot(Booking booking) {
        TimeSlot slot = timeSlotRepository.findById(booking.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(
                    "Créneau introuvable pour cette réservation : id=" + booking.getTimeSlotId()));
        slot.setStatus(SlotStatus.AVAILABLE);
        slot.setBookingId(null);
        timeSlotRepository.save(slot);
    }

    private void validateCancellationDeadline(TimeSlot slot) {
        LocalDateTime slotStart = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime deadline = slotStart.minusHours(CANCELLATION_DEADLINE_HOURS);

        if (LocalDateTime.now().isAfter(deadline)) {
            throw new InvalidBookingException(
                "Annulation impossible : le rendez-vous a lieu le "
                    + slotStart.format(DEADLINE_FORMATTER)
                    + ", soit dans moins de " + CANCELLATION_DEADLINE_HOURS
                    + "h. Contactez directement l'autre partie pour convenir d'une solution.");
        }
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