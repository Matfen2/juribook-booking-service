package juribook.booking_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.repository.TimeSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests de BookingEventPublisherImpl, en particulier l'enrichissement
 * slotDate/slotStartTime nécessaire à la métrique "heures
 * de pointe" côté audit-service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingEventPublisherImpl")
class BookingEventPublisherImplTest {

    @Mock private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private TimeSlotRepository timeSlotRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private BookingEventPublisherImpl publisher;

    private Booking booking;
    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        publisher = new BookingEventPublisherImpl(kafkaTemplateProvider, objectMapper, timeSlotRepository);

        booking = new Booking();
        booking.setId(900L);
        booking.setClientId(42L);
        booking.setLawyerId(10L);
        booking.setTimeSlotId(501L);
        booking.setStatus(BookingStatus.PENDING);
        booking.setReason("Litige avec mon employeur");

        slot = new TimeSlot();
        slot.setId(501L);
        slot.setDate(LocalDate.of(2026, 7, 10));
        slot.setStartTime(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("cas nominal - enrichit le payload avec la date et l'heure du créneau")
    void publishBookingCreated_enrichesPayloadWithSlotDateAndTime() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(timeSlotRepository.findById(501L)).thenReturn(Optional.of(slot));

        publisher.publishBookingCreated(booking);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("booking-events"), eq("900"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .contains("\"eventType\":\"booking.created\"")
                .contains("\"slotDate\":\"2026-07-10\"")
                .contains("\"slotStartTime\":\"14:30:00\"");
    }

    @Test
    @DisplayName("TimeSlot introuvable - publie quand même, sans slotDate/slotStartTime, ne plante pas")
    void publishBookingCreated_slotNotFound_publishesWithoutSlotInfo() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(timeSlotRepository.findById(501L)).thenReturn(Optional.empty());

        assertThatCode(() -> publisher.publishBookingCreated(booking)).doesNotThrowAnyException();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("booking-events"), eq("900"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .contains("\"eventType\":\"booking.created\"")
                .contains("\"slotDate\":null")
                .contains("\"slotStartTime\":null");
    }

    @Test
    @DisplayName("Kafka indisponible - ne consulte même pas TimeSlotRepository (court-circuit)")
    void publishBookingCreated_kafkaUnavailable_doesNotQueryTimeSlot() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);

        publisher.publishBookingCreated(booking);

        verifyNoInteractions(timeSlotRepository);
    }

    @Test
    @DisplayName("publishBookingCancelled - eventType correct, même enrichissement")
    void publishBookingCancelled_correctEventTypeAndEnrichment() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(timeSlotRepository.findById(501L)).thenReturn(Optional.of(slot));

        publisher.publishBookingCancelled(booking);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("booking-events"), eq("900"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue())
                .contains("\"eventType\":\"booking.cancelled\"")
                .contains("\"slotDate\":\"2026-07-10\"");
    }
}