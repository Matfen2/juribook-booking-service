package juribook.booking_service.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de TimeSlot#isBookable()
 *
 * Règle : un créneau est réservable s'il est AVAILABLE ET que sa date/heure
 * de début n'est pas déjà passée.
 */
class TimeSlotTest {

    private TimeSlot slot;

    @BeforeEach
    void setUp() {
        slot = new TimeSlot();
        slot.setLawyerId(1L);
        slot.setStatus(SlotStatus.AVAILABLE);
    }

    @Test
    void isBookable_true_whenAvailableAndDateInFuture() {
        slot.setDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));

        assertThat(slot.isBookable()).isTrue();
    }

    @Test
    void isBookable_false_whenDateInPast() {
        slot.setDate(LocalDate.now().minusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));

        assertThat(slot.isBookable()).isFalse();
    }

    @Test
    void isBookable_false_whenTodayButStartTimeAlreadyPassed() {
        slot.setDate(LocalDate.now());
        slot.setStartTime(LocalTime.now().minusHours(1));
        slot.setEndTime(LocalTime.now().minusMinutes(30));

        assertThat(slot.isBookable()).isFalse();
    }

    @Test
    void isBookable_true_whenTodayAndStartTimeStillInFuture() {
        // Garde-fou : si le test tourne très tard le soir, on évite le passage à minuit
        LocalTime in2Hours = LocalTime.now().plusHours(2);
        if (in2Hours.isBefore(LocalTime.now())) {
            return; // évite un faux négatif si plusHours franchit minuit
        }
        slot.setDate(LocalDate.now());
        slot.setStartTime(in2Hours);
        slot.setEndTime(in2Hours.plusMinutes(30));

        assertThat(slot.isBookable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SlotStatus.class, names = {"BOOKED", "BLOCKED", "CANCELLED", "COMPLETED"})
    void isBookable_false_whenStatusIsNotAvailable(SlotStatus status) {
        slot.setStatus(status);
        slot.setDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));

        assertThat(slot.isBookable()).isFalse();
    }
}