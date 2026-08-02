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
        // Garde-fou : si l'heure actuelle est trop proche de minuit,
        // LocalTime.now().minusHours(1) "wrappe" vers la veille (23h passé),
        // qui se compare alors comme PLUS TARD que l'heure actuelle
        // (LocalTime n'a pas de notion de date), faux négatif sinon.
        // Même risque que le test suivant, qui a déjà ce garde-fou.
        LocalTime oneHourAgo = LocalTime.now().minusHours(1);
        if (oneHourAgo.isAfter(LocalTime.now())) {
            return; // heure actuelle < 1h du matin, on évite le faux négatif
        }

        slot.setDate(LocalDate.now());
        slot.setStartTime(oneHourAgo);
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