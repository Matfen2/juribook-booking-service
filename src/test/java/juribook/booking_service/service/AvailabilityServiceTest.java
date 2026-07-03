package juribook.booking_service.service;

import juribook.booking_service.dto.request.CreateAvailabilityRequest;
import juribook.booking_service.dto.response.AvailabilityResponse;
import juribook.booking_service.entity.Availability;
import juribook.booking_service.entity.TimeSlot;
import juribook.booking_service.exception.AvailabilityNotFoundException;
import juribook.booking_service.exception.InvalidAvailabilityException;
import juribook.booking_service.repository.AvailabilityRepository;
import juribook.booking_service.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests de AvailabilityService — Sprint 3.7.
 *
 * Couvre : pas de chevauchement entre disponibilités récurrentes du même
 * jour, génération correcte des TimeSlot, idempotence de la génération.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    private static final Long LAWYER_ID = 1L;

    // ══════════════════════════════════════════════════════════
    //  Validation de plage horaire
    // ══════════════════════════════════════════════════════════

    @Test
    void createAvailability_throws_whenEndTimeNotAfterStartTime() {
        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(17, 0), LocalTime.of(9, 0), 30, 1);

        assertThatThrownBy(() -> availabilityService.createAvailability(LAWYER_ID, request))
                .isInstanceOf(InvalidAvailabilityException.class)
                .hasMessageContaining("heure de fin");

        verifyNoInteractions(timeSlotRepository);
    }

    // ══════════════════════════════════════════════════════════
    //  Pas de chevauchement possible (disponibilités récurrentes)
    // ══════════════════════════════════════════════════════════

    @Test
    void createAvailability_throws_whenOverlapsActiveAvailabilitySameDay() {
        Availability existing = buildAvailability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), true);
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(LAWYER_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(existing));

        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(18, 0), 30, 1);

        assertThatThrownBy(() -> availabilityService.createAvailability(LAWYER_ID, request))
                .isInstanceOf(InvalidAvailabilityException.class)
                .hasMessageContaining("chevauche");

        verify(availabilityRepository, never()).save(any());
    }

    @Test
    void createAvailability_allowsOverlap_whenExistingAvailabilityIsInactive() {
        Availability existing = buildAvailability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), false);
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(LAWYER_ID, DayOfWeek.MONDAY))
                .thenReturn(List.of(existing));
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> {
            Availability a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });
        when(timeSlotRepository.findByLawyerIdAndDate(anyLong(), any(LocalDate.class))).thenReturn(List.of());

        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 1);

        AvailabilityResponse response = availabilityService.createAvailability(LAWYER_ID, request);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    void createAvailability_allowsDifferentDayOfWeek_evenIfSameHours() {
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(LAWYER_ID, DayOfWeek.TUESDAY))
                .thenReturn(List.of()); // aucune dispo le mardi
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> {
            Availability a = inv.getArgument(0);
            a.setId(11L);
            return a;
        });
        when(timeSlotRepository.findByLawyerIdAndDate(anyLong(), any(LocalDate.class))).thenReturn(List.of());

        CreateAvailabilityRequest request = buildRequest(DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30, 1);

        AvailabilityResponse response = availabilityService.createAvailability(LAWYER_ID, request);

        assertThat(response.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    }

    // ══════════════════════════════════════════════════════════
    //  Génération de créneaux concrets
    // ══════════════════════════════════════════════════════════

    @Test
    void createAvailability_generatesCorrectNumberOfSlots_forOneWeekWindow() {
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(eq(LAWYER_ID), any())).thenReturn(List.of());
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> {
            Availability a = inv.getArgument(0);
            a.setId(20L);
            return a;
        });
        when(timeSlotRepository.findByLawyerIdAndDate(anyLong(), any(LocalDate.class))).thenReturn(List.of());

        // 9h-10h, créneaux de 30min, génération sur 1 semaine → 1 seule occurrence
        // du jour de semaine dans la fenêtre → 2 créneaux (9h-9h30, 9h30-10h)
        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30, 1);

        AvailabilityResponse response = availabilityService.createAvailability(LAWYER_ID, request);

        assertThat(response.getGeneratedSlotsCount()).isEqualTo(2);
        verify(timeSlotRepository, times(2)).save(any(TimeSlot.class));
    }

    @Test
    void createAvailability_isIdempotent_skipsAlreadyExistingSlots() {
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(eq(LAWYER_ID), any())).thenReturn(List.of());
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> {
            Availability a = inv.getArgument(0);
            a.setId(21L);
            return a;
        });

        // Simule un créneau 9h-9h30 déjà présent en base pour toutes les dates interrogées
        TimeSlot alreadyExists = new TimeSlot();
        alreadyExists.setStartTime(LocalTime.of(9, 0));
        when(timeSlotRepository.findByLawyerIdAndDate(anyLong(), any(LocalDate.class)))
                .thenReturn(List.of(alreadyExists));

        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30, 1);

        AvailabilityResponse response = availabilityService.createAvailability(LAWYER_ID, request);

        // Seul le créneau 9h30-10h est créé, le 9h-9h30 est ignoré (pas de doublon)
        assertThat(response.getGeneratedSlotsCount()).isEqualTo(1);
        ArgumentCaptor<TimeSlot> captor = ArgumentCaptor.forClass(TimeSlot.class);
        verify(timeSlotRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void createAvailability_usesDefaultFourWeeks_whenGenerationWeeksNotProvided() {
        when(availabilityRepository.findByLawyerIdAndDayOfWeek(eq(LAWYER_ID), any())).thenReturn(List.of());
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> {
            Availability a = inv.getArgument(0);
            a.setId(22L);
            return a;
        });
        when(timeSlotRepository.findByLawyerIdAndDate(anyLong(), any(LocalDate.class))).thenReturn(List.of());

        // generationWeeks = null → défaut 4 semaines → 4 occurrences du jour visé
        // x 2 créneaux (9h-10h par tranches de 30min) = 8 créneaux
        CreateAvailabilityRequest request = buildRequest(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(10, 0), 30, null);

        AvailabilityResponse response = availabilityService.createAvailability(LAWYER_ID, request);

        assertThat(response.getGeneratedSlotsCount()).isEqualTo(8);
    }

    // ══════════════════════════════════════════════════════════
    //  deactivate
    // ══════════════════════════════════════════════════════════

    @Test
    void deactivate_throws_whenAvailabilityNotFound() {
        when(availabilityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.deactivate(LAWYER_ID, 99L))
                .isInstanceOf(AvailabilityNotFoundException.class);
    }

    @Test
    void deactivate_throws_whenAvailabilityBelongsToAnotherLawyer() {
        Availability availability = buildAvailability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), true);
        availability.setId(5L);
        availability.setLawyerId(999L);
        when(availabilityRepository.findById(5L)).thenReturn(Optional.of(availability));

        assertThatThrownBy(() -> availabilityService.deactivate(LAWYER_ID, 5L))
                .isInstanceOf(InvalidAvailabilityException.class)
                .hasMessageContaining("n'appartient pas");
    }

    @Test
    void deactivate_succeeds_setsActiveFalse_keepsGeneratedSlots() {
        Availability availability = buildAvailability(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), true);
        availability.setId(5L);
        when(availabilityRepository.findById(5L)).thenReturn(Optional.of(availability));
        when(availabilityRepository.save(any(Availability.class))).thenAnswer(inv -> inv.getArgument(0));

        AvailabilityResponse response = availabilityService.deactivate(LAWYER_ID, 5L);

        assertThat(response.isActive()).isFalse();
        verifyNoInteractions(timeSlotRepository); // les créneaux déjà générés ne sont pas touchés
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private CreateAvailabilityRequest buildRequest(DayOfWeek day, LocalTime start, LocalTime end,
                                                     int durationMinutes, Integer generationWeeks) {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest();
        request.setDayOfWeek(day);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setSlotDurationMinutes(durationMinutes);
        request.setGenerationWeeks(generationWeeks);
        return request;
    }

    private Availability buildAvailability(DayOfWeek day, LocalTime start, LocalTime end, boolean active) {
        Availability availability = new Availability();
        availability.setLawyerId(LAWYER_ID);
        availability.setDayOfWeek(day);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setSlotDurationMinutes(30);
        availability.setActive(active);
        availability.setValidFrom(LocalDate.now());
        return availability;
    }
}