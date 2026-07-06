package juribook.booking_service.service;

import juribook.booking_service.dto.request.BlockPeriodRequest;
import juribook.booking_service.dto.request.CreateTimeSlotRequest;
import juribook.booking_service.dto.response.BlockPeriodResponse;
import juribook.booking_service.dto.response.TimeSlotResponse;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.InvalidTimeSlotException;
import juribook.booking_service.exception.TimeSlotNotFoundException;
import juribook.booking_service.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de TimeSlotService
 *
 * Couvre : pas de chevauchement possible (création de créneau ponctuel),
 * créneau dans le passé refusé, suppression protégée (BOOKED/COMPLETED),
 * blocage/déblocage de période, filtres de consultation.
 */
@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @InjectMocks
    private TimeSlotService timeSlotService;

    private static final Long LAWYER_ID = 1L;

    // ══════════════════════════════════════════════════════════
    //  createTimeSlot - validation de plage horaire
    // ══════════════════════════════════════════════════════════
    @Test
    void createTimeSlot_throws_whenEndTimeNotAfterStartTime() {
        CreateTimeSlotRequest request = buildRequest(LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("heure de fin");

        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createTimeSlot_throws_whenEndTimeEqualsStartTime() {
        CreateTimeSlotRequest request = buildRequest(LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  createTimeSlot - créneaux passés
    // ══════════════════════════════════════════════════════════
    @Test
    void createTimeSlot_throws_whenDateTimeInPast() {
        CreateTimeSlotRequest request = buildRequest(LocalDate.now().minusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("passé");

        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createTimeSlot_throws_whenTodayButStartTimeAlreadyPassed() {
        // Garde-fou : si l'heure actuelle est trop proche de minuit,
        // LocalTime.now().minusHours(1) "wrappe" vers la veille (23h passé),
        // qui se compare alors comme PLUS TARD que l'heure actuelle
        // (LocalTime n'a pas de notion de date), le service ne détecterait
        // plus le créneau comme passé, et save() (non stubbé) renverrait
        // null → NullPointerException au lieu de InvalidTimeSlotException.
        // Même risque que TimeSlotTest#isBookable_false_whenTodayButStartTimeAlreadyPassed.
        LocalTime oneHourAgo = LocalTime.now().minusHours(1);
        if (oneHourAgo.isAfter(LocalTime.now())) {
            return; // heure actuelle < 1h du matin, on évite le faux négatif
        }

        CreateTimeSlotRequest request = buildRequest(LocalDate.now(),
                oneHourAgo, LocalTime.now().minusMinutes(30));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("passé");
    }

    // ══════════════════════════════════════════════════════════
    //  createTimeSlot - pas de chevauchement possible
    // ══════════════════════════════════════════════════════════
    @Test
    void createTimeSlot_throws_whenExactSameSlotExists() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot existing = existingSlot(date, LocalTime.of(9, 0), LocalTime.of(9, 30));
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of(existing));

        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 0), LocalTime.of(9, 30));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("chevauche");
    }

    @Test
    void createTimeSlot_throws_whenPartialOverlapAtStart() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot existing = existingSlot(date, LocalTime.of(9, 0), LocalTime.of(9, 30));
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of(existing));

        // nouveau créneau 9h15-9h45 chevauche la fin de l'existant
        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 15), LocalTime.of(9, 45));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void createTimeSlot_throws_whenNewSlotContainsExisting() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot existing = existingSlot(date, LocalTime.of(9, 15), LocalTime.of(9, 45));
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of(existing));

        // nouveau créneau englobe entièrement l'existant
        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void createTimeSlot_succeeds_whenAdjacentToExisting_noOverlap() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot existing = existingSlot(date, LocalTime.of(9, 0), LocalTime.of(9, 30));
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of(existing));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        // nouveau créneau commence exactement quand l'existant se termine → pas de chevauchement
        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 30), LocalTime.of(10, 0));

        TimeSlotResponse response = timeSlotService.createTimeSlot(LAWYER_ID, request);

        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        verify(timeSlotRepository).save(any(TimeSlot.class));
    }

    @Test
    void createTimeSlot_succeeds_whenDifferentDay_evenIfSameHours() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of());
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 0), LocalTime.of(9, 30));

        TimeSlotResponse response = timeSlotService.createTimeSlot(LAWYER_ID, request);

        assertThat(response.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void createTimeSlot_convertsDataIntegrityViolation_toInvalidTimeSlotException() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(timeSlotRepository.findByLawyerIdAndDate(LAWYER_ID, date)).thenReturn(List.of());
        when(timeSlotRepository.save(any(TimeSlot.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte UNIQUE violée"));

        CreateTimeSlotRequest request = buildRequest(date, LocalTime.of(9, 0), LocalTime.of(9, 30));

        assertThatThrownBy(() -> timeSlotService.createTimeSlot(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  deleteTimeSlot - protection BOOKED / COMPLETED
    // ══════════════════════════════════════════════════════════
    @Test
    void deleteTimeSlot_throws_whenSlotNotFound() {
        when(timeSlotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timeSlotService.deleteTimeSlot(LAWYER_ID, 99L))
                .isInstanceOf(TimeSlotNotFoundException.class);
    }

    @Test
    void deleteTimeSlot_throws_whenSlotBelongsToAnotherLawyer() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        slot.setLawyerId(999L);
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotService.deleteTimeSlot(LAWYER_ID, 5L))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("n'appartient pas");
    }

    @Test
    void deleteTimeSlot_throws_whenStatusBooked() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        slot.setStatus(SlotStatus.BOOKED);
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotService.deleteTimeSlot(LAWYER_ID, 5L))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("réservé");

        verify(timeSlotRepository, never()).delete(any());
    }

    @Test
    void deleteTimeSlot_throws_whenStatusCompleted() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        slot.setStatus(SlotStatus.COMPLETED);
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotService.deleteTimeSlot(LAWYER_ID, 5L))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("honoré");
    }

    @Test
    void deleteTimeSlot_succeeds_whenStatusAvailable() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        timeSlotService.deleteTimeSlot(LAWYER_ID, 5L);

        verify(timeSlotRepository).delete(slot);
    }

    // ══════════════════════════════════════════════════════════
    //  blockPeriod
    // ══════════════════════════════════════════════════════════
    @Test
    void blockPeriod_throws_whenToDateBeforeFromDate() {
        BlockPeriodRequest request = new BlockPeriodRequest();
        request.setFromDate(LocalDate.now().plusDays(5));
        request.setToDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> timeSlotService.blockPeriod(LAWYER_ID, request))
                .isInstanceOf(InvalidTimeSlotException.class);

        verifyNoInteractions(timeSlotRepository);
    }

    @Test
    void blockPeriod_blocksOnlyAvailableSlotsInRange_andSetsReason() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(7);

        TimeSlot s1 = existingSlot(from, LocalTime.of(9, 0), LocalTime.of(9, 30));
        TimeSlot s2 = existingSlot(to, LocalTime.of(10, 0), LocalTime.of(10, 30));
        when(timeSlotRepository.findAvailableSlotsInRange(LAWYER_ID, from, to)).thenReturn(List.of(s1, s2));
        when(timeSlotRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        BlockPeriodRequest request = new BlockPeriodRequest();
        request.setFromDate(from);
        request.setToDate(to);
        request.setReason("Congés");

        BlockPeriodResponse response = timeSlotService.blockPeriod(LAWYER_ID, request);

        assertThat(response.getBlockedSlotsCount()).isEqualTo(2);
        assertThat(s1.getStatus()).isEqualTo(SlotStatus.BLOCKED);
        assertThat(s1.getBlockReason()).isEqualTo("Congés");
        assertThat(s2.getStatus()).isEqualTo(SlotStatus.BLOCKED);
    }

    @Test
    void unblockSlot_throws_whenStatusNotBlocked() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        slot.setStatus(SlotStatus.AVAILABLE);
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> timeSlotService.unblockSlot(LAWYER_ID, 5L))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void unblockSlot_succeeds_setsAvailableAndClearsReason() {
        TimeSlot slot = existingSlot(LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        slot.setId(5L);
        slot.setStatus(SlotStatus.BLOCKED);
        slot.setBlockReason("Congés");
        when(timeSlotRepository.findById(5L)).thenReturn(Optional.of(slot));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeSlotResponse response = timeSlotService.unblockSlot(LAWYER_ID, 5L);

        assertThat(response.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(response.getBlockReason()).isNull();
    }

    // ══════════════════════════════════════════════════════════
    //  getSlots - filtres
    // ══════════════════════════════════════════════════════════
    @Test
    void getSlots_withDateParam_defaultsToAvailableOnly() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot available = existingSlot(date, LocalTime.of(9, 0), LocalTime.of(9, 30));
        TimeSlot booked = existingSlot(date, LocalTime.of(10, 0), LocalTime.of(10, 30));
        booked.setStatus(SlotStatus.BOOKED);
        when(timeSlotRepository.findByLawyerIdInRange(LAWYER_ID, date, date))
                .thenReturn(List.of(available, booked));

        List<TimeSlotResponse> result = timeSlotService.getSlots(LAWYER_ID, date, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void getSlots_withDateParam_explicitStatusOverridesDefault() {
        LocalDate date = LocalDate.now().plusDays(1);
        TimeSlot booked = existingSlot(date, LocalTime.of(10, 0), LocalTime.of(10, 30));
        booked.setStatus(SlotStatus.BOOKED);
        when(timeSlotRepository.findByLawyerIdInRange(LAWYER_ID, date, date)).thenReturn(List.of(booked));

        List<TimeSlotResponse> result = timeSlotService.getSlots(LAWYER_ID, date, null, null, SlotStatus.BOOKED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void getSlots_withoutDateParam_returnsAllStatuses_andDefaultsRange() {
        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(timeSlotRepository.findByLawyerIdInRange(eq(LAWYER_ID), fromCaptor.capture(), toCaptor.capture()))
                .thenReturn(List.of());

        timeSlotService.getSlots(LAWYER_ID, null, null, null, null);

        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.now());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void getSlots_withFromToRange_noStatusFilter_returnsEverything() {
        LocalDate from = LocalDate.now();
        LocalDate to = LocalDate.now().plusWeeks(2);
        TimeSlot available = existingSlot(from, LocalTime.of(9, 0), LocalTime.of(9, 30));
        TimeSlot blocked = existingSlot(to, LocalTime.of(10, 0), LocalTime.of(10, 30));
        blocked.setStatus(SlotStatus.BLOCKED);
        when(timeSlotRepository.findByLawyerIdInRange(LAWYER_ID, from, to))
                .thenReturn(List.of(available, blocked));

        List<TimeSlotResponse> result = timeSlotService.getSlots(LAWYER_ID, null, from, to, null);

        assertThat(result).hasSize(2);
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════
    private CreateTimeSlotRequest buildRequest(LocalDate date, LocalTime start, LocalTime end) {
        CreateTimeSlotRequest request = new CreateTimeSlotRequest();
        request.setDate(date);
        request.setStartTime(start);
        request.setEndTime(end);
        return request;
    }

    private TimeSlot existingSlot(LocalDate date, LocalTime start, LocalTime end) {
        TimeSlot slot = new TimeSlot();
        slot.setLawyerId(LAWYER_ID);
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setStatus(SlotStatus.AVAILABLE);
        return slot;
    }
}