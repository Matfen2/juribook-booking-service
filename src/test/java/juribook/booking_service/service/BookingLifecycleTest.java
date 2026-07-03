package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateBookingRequest;
import juribook.booking_service.dto.response.BookingResponse;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.event.BookingEventPublisher;
import juribook.booking_service.event.SlotEventPublisher;
import juribook.booking_service.repository.BookingRepository;
import juribook.booking_service.repository.LawyerStatusCacheRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de cycle de vie complet d'une réservation.
 *
 * Contrairement à BookingServiceTest (qui isole chaque méthode avec des
 * mocks frais), ces tests enchaînent plusieurs appels sur la même
 * instance de BookingService en réutilisant l'état produit par l'étape
 * précédente, pour vérifier que les transitions de statut restent
 * cohérentes de bout en bout.
 *
 * LawyerStatusCacheRepository : aucun stubbing nécessaire,
 * Mockito retourne Optional.empty() par défaut pour findById() non
 * stubbé, ce qui correspond au comportement "fail-open" attendu.
 */
@ExtendWith(MockitoExtension.class)
class BookingLifecycleTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private LawyerStatusCacheRepository lawyerStatusCacheRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private SlotEventPublisher slotEventPublisher;

    @InjectMocks
    private BookingService bookingService;

    private static final Long CLIENT_ID = 42L;
    private static final Long LAWYER_ID = 1L;
    private static final Long SLOT_ID = 67L;
    private static final Long BOOKING_ID = 7L;

    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setLawyerId(LAWYER_ID);
        slot.setDate(LocalDate.now().plusDays(3));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setStatus(SlotStatus.AVAILABLE);
    }

    @Test
    void fullCycle_createThenConfirmThenCancel_transitionsCorrectlyAtEachStep() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTimeSlotId(SLOT_ID);
        request.setReason("Litige avec mon employeur");

        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(BOOKING_ID);
            return b;
        });

        BookingResponse created = bookingService.createBooking(CLIENT_ID, request);

        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slot.getBookingId()).isEqualTo(BOOKING_ID);
        verify(bookingEventPublisher).publishBookingCreated(any(Booking.class));

        Booking persistedBooking = new Booking();
        persistedBooking.setId(BOOKING_ID);
        persistedBooking.setClientId(CLIENT_ID);
        persistedBooking.setLawyerId(LAWYER_ID);
        persistedBooking.setTimeSlotId(SLOT_ID);
        persistedBooking.setReason("Litige avec mon employeur");
        persistedBooking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(persistedBooking));

        BookingResponse confirmed = bookingService.confirmBooking(LAWYER_ID, BOOKING_ID);

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingEventPublisher).publishBookingConfirmed(persistedBooking);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        when(timeSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

        BookingResponse cancelled = bookingService.cancelBooking(CLIENT_ID, "CLIENT", BOOKING_ID);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slot.getBookingId()).isNull();

        verify(bookingEventPublisher).publishBookingCancelled(persistedBooking);
        verify(slotEventPublisher).publishSlotReleased(LAWYER_ID, SLOT_ID);

        verify(bookingEventPublisher, times(1)).publishBookingCreated(any());
        verify(bookingEventPublisher, times(1)).publishBookingConfirmed(any());
        verify(bookingEventPublisher, times(1)).publishBookingCancelled(any());
    }

    @Test
    void fullCycle_createThenReject_releasesSlotImmediately_withoutConfirmation() {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTimeSlotId(SLOT_ID);
        request.setReason("Consultation urgente");

        when(timeSlotRepository.findByIdForUpdate(SLOT_ID)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(BOOKING_ID);
            return b;
        });

        BookingResponse created = bookingService.createBooking(CLIENT_ID, request);
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);

        Booking persistedBooking = new Booking();
        persistedBooking.setId(BOOKING_ID);
        persistedBooking.setLawyerId(LAWYER_ID);
        persistedBooking.setTimeSlotId(SLOT_ID);
        persistedBooking.setStatus(BookingStatus.PENDING);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(persistedBooking));
        when(timeSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

        BookingResponse rejected = bookingService.rejectBooking(LAWYER_ID, BOOKING_ID);

        assertThat(rejected.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(slot.getBookingId()).isNull();

        verify(bookingEventPublisher, never()).publishBookingConfirmed(any());
        verify(bookingEventPublisher).publishBookingCancelled(persistedBooking);
        verify(slotEventPublisher).publishSlotReleased(LAWYER_ID, SLOT_ID);
    }
}