package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.event.BookingEventPublisher;
import juribook.booking_service.event.SlotEventPublisher;
import juribook.booking_service.exception.BookingConflictException;
import juribook.booking_service.exception.InvalidTimeSlotException;
import juribook.booking_service.exception.TimeSlotNotFoundException;
import juribook.booking_service.repository.BookingRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de BookingService - réservation + double
 * réservation, 409 + publication d'événements Kafka.
 *
 * BookingEventPublisher et SlotEventPublisher sont mockés (jamais null)
 * depuis que BookingService en dépend dans son constructeur, sans ces
 * mocks, @InjectMocks injecte null et toute méthode qui publie un
 * événement lève une NullPointerException.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private SlotEventPublisher slotEventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private static final Long CLIENT_ID = 42L;
    private static final Long LAWYER_ID = 1L;
    private static final Long SLOT_ID = 67L;

    private TimeSlot slot;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setLawyerId(LAWYER_ID);
        slot.setDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setStatus(SlotStatus.AVAILABLE);

        request = new CreateBookingRequest();
        request.setTimeSlotId(SLOT_ID);
        request.setReason("Litige avec mon employeur");
    }

    // ══════════════════════════════════════════════════════════
    //  Cas nominal
    // ══════════════════════════════════════════════════════════
    @Test
    void createBooking_succeeds_whenSlotAvailable() {
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(CLIENT_ID, request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(response.getLawyerId()).isEqualTo(LAWYER_ID);
        assertThat(response.getTimeSlotId()).isEqualTo(SLOT_ID);
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getReason()).isEqualTo("Litige avec mon employeur");
    }

    @Test
    void createBooking_marksSlotAsBooked_andLinksBookingId() {
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(7L);
            return b;
        });

        bookingService.createBooking(CLIENT_ID, request);

        ArgumentCaptor<TimeSlot> slotCaptor = ArgumentCaptor.forClass(TimeSlot.class);
        verify(timeSlotRepository).save(slotCaptor.capture());
        assertThat(slotCaptor.getValue().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slotCaptor.getValue().getBookingId()).isEqualTo(7L);
    }

    @Test
    void createBooking_publishesBookingCreatedEvent() {
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        bookingService.createBooking(CLIENT_ID, request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingEventPublisher).publishBookingCreated(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        // Pas de libération de créneau à la création, seul reject/cancel le font
        verifyNoInteractions(slotEventPublisher);
    }

    @Test
    void createBooking_throws_whenSlotNotFound() {
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(CLIENT_ID, request))
                .isInstanceOf(TimeSlotNotFoundException.class);

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingEventPublisher);
    }

    @Test
    void createBooking_throws_whenSlotInPast() {
        slot.setDate(LocalDate.now().minusDays(1));
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> bookingService.createBooking(CLIENT_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("passé");

        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(bookingEventPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = SlotStatus.class, names = {"BLOCKED", "COMPLETED", "CANCELLED"})
    void createBooking_throws_InvalidTimeSlotException_whenSlotNotAvailableButNotBooked(SlotStatus status) {
        slot.setStatus(status);
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));

        // Ces statuts ne sont pas un "conflit de double réservation" (409),
        // mais une indisponibilité ordinaire (400).
        assertThatThrownBy(() -> bookingService.createBooking(CLIENT_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .isNotInstanceOf(BookingConflictException.class);

        verifyNoInteractions(bookingRepository);
    }

    // ══════════════════════════════════════════════════════════
    //  Double réservation - 409 Conflict
    // ══════════════════════════════════════════════════════════
    @Test
    void createBooking_throws_BookingConflictException_whenSlotAlreadyBooked() {
        slot.setStatus(SlotStatus.BOOKED);
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> bookingService.createBooking(CLIENT_ID, request))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("déjà réservé");

        verifyNoInteractions(bookingRepository);
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createBooking_usesPessimisticLock_notPlainFindById() {
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        bookingService.createBooking(CLIENT_ID, request);

        // Garantit que le verrou pessimiste est bien utilisé pour la lecture
        // du créneau, et pas la méthode findById standard (sans verrou).
        verify(timeSlotRepository).findByIdForUpdate(SLOT_ID);
        verify(timeSlotRepository, never()).findById(any());
    }

    @Test
    void createBooking_throws_BookingConflictException_whenDataIntegrityViolationOnSave() {
        // Simule une collision détectée par l'index unique partiel (V5),
        // malgré le verrou pessimiste, filet de sécurité de dernier recours.
        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte unique violée"));

        assertThatThrownBy(() -> bookingService.createBooking(CLIENT_ID, request))
                .isInstanceOf(BookingConflictException.class);

        verify(timeSlotRepository, never()).save(any());
        verifyNoInteractions(bookingEventPublisher);
    }
}