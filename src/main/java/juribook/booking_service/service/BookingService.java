package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.event.BookingEventPublisher;
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

/**
 * Service métier pour la réservation de créneaux par les clients, leur
 * traitement par les avocats, leur annulation, et la publication des
 * événements associés sur Kafka.
 *
 * ⚠️ Limite connue : confirmBooking, rejectBooking et
 * cancelBooking (côté avocat) ne vérifient PAS que le Booking appartient
 * bien à l'avocat authentifié, même limitation que sur
 * AvailabilityController/TimeSlotController (pas de résolution
 * authUserId → lawyerId sans appel au lawyer-service). Côté client en
 * revanche, l'appartenance EST vérifiée pour cancelBooking, car
 * Booking.clientId est directement l'authUserId extrait du JWT (pas
 * besoin d'appel inter-services). À corriger côté avocat avant la mise
 * en production.
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
    private final BookingEventPublisher eventPublisher;

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

        eventPublisher.publishBookingCreated(saved);

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
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.PENDING);

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);

        log.info("Réservation confirmée : bookingId={}, lawyerId={}", bookingId, lawyerId);
        eventPublisher.publishBookingConfirmed(saved);

        return BookingResponse.from(saved);
    }

    /**
     * L'avocat refuse une demande de réservation en attente.
     * Le Booking passe en CANCELLED et le créneau est immédiatement
     * libéré (retour à AVAILABLE) pour redevenir réservable par un
     * autre client. Pas de règle des 24h ici : la demande n'a jamais
     * été confirmée, rien n'a été engagé côté agenda de l'avocat.
     */
    @Transactional
    public BookingResponse rejectBooking(Long lawyerId, Long bookingId) {
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.PENDING);

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        releaseSlot(booking);

        log.info("Réservation refusée : bookingId={}, lawyerId={}, timeSlotId={} libéré",
                bookingId, lawyerId, booking.getTimeSlotId());
        eventPublisher.publishBookingCancelled(savedBooking);

        return BookingResponse.from(savedBooking);
    }

    // ══════════════════════════════════════════════════════════
    //  Annulation - client ou avocat, règle des 24h
    // ══════════════════════════════════════════════════════════
    /**
     * Annule une réservation CONFIRMED, à la demande du client ou de
     * l'avocat. Refusée si le rendez-vous a lieu dans moins de 24h.
     *
     * Une réservation PENDING ne passe pas par ce endpoint : côté avocat,
     * c'est /reject (pas de règle des 24h, rien n'était confirmé). Côté
     * client, une demande PENDING peut être laissée à l'avocat pour
     * refus, il n'y a pas encore de rendez-vous "confirmé" à décommander.
     *
     * @param actorId   authUserId extrait du JWT de l'appelant
     * @param actorRole "CLIENT" ou "LAWYER", extrait du rôle du JWT
     */
    @Transactional
    public BookingResponse cancelBooking(Long actorId, String actorRole, Long bookingId) {
        Booking booking = getBookingInStatusOrThrow(bookingId, BookingStatus.CONFIRMED);

        if ("CLIENT".equals(actorRole) && !booking.getClientId().equals(actorId)) {
            throw new AccessDeniedException("Cette réservation n'appartient pas à ce client");
        }
        // Côté LAWYER : pas de vérification d'appartenance possible sans
        // appel au lawyer-service, cf. limite connue en tête de classe.

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
        eventPublisher.publishBookingCancelled(savedBooking);

        return BookingResponse.from(savedBooking);
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