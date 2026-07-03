package juribook.booking_service.service;

import juribook.booking_service.dto.response.WaitlistEntryResponse;
import juribook.booking_service.entity.WaitlistEntry;
import juribook.booking_service.exception.AlreadyOnWaitlistException;
import juribook.booking_service.repository.WaitlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests de WaitlistService.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @InjectMocks
    private WaitlistService waitlistService;

    private static final Long LAWYER_ID = 1L;
    private static final Long CLIENT_ID = 42L;

    // ══════════════════════════════════════════════════════════
    //  joinWaitlist
    // ══════════════════════════════════════════════════════════
    @Test
    void joinWaitlist_succeeds_whenNotAlreadyRegistered() {
        when(waitlistRepository.findByLawyerIdAndClientId(LAWYER_ID, CLIENT_ID)).thenReturn(Optional.empty());
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> {
            WaitlistEntry e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.now());
            return e;
        });

        WaitlistEntryResponse response = waitlistService.joinWaitlist(LAWYER_ID, CLIENT_ID);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getLawyerId()).isEqualTo(LAWYER_ID);
        assertThat(response.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void joinWaitlist_persistsCorrectLawyerAndClientId() {
        when(waitlistRepository.findByLawyerIdAndClientId(LAWYER_ID, CLIENT_ID)).thenReturn(Optional.empty());
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        waitlistService.joinWaitlist(LAWYER_ID, CLIENT_ID);

        ArgumentCaptor<WaitlistEntry> captor = ArgumentCaptor.forClass(WaitlistEntry.class);
        verify(waitlistRepository).save(captor.capture());
        assertThat(captor.getValue().getLawyerId()).isEqualTo(LAWYER_ID);
        assertThat(captor.getValue().getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void joinWaitlist_throws_whenAlreadyRegistered() {
        WaitlistEntry existing = new WaitlistEntry();
        existing.setId(1L);
        existing.setLawyerId(LAWYER_ID);
        existing.setClientId(CLIENT_ID);
        when(waitlistRepository.findByLawyerIdAndClientId(LAWYER_ID, CLIENT_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> waitlistService.joinWaitlist(LAWYER_ID, CLIENT_ID))
                .isInstanceOf(AlreadyOnWaitlistException.class)
                .hasMessageContaining("déjà inscrit");

        verify(waitlistRepository, never()).save(any());
    }

    @Test
    void joinWaitlist_throws_onConcurrentDuplicate_viaDataIntegrityViolation() {
        // Simule deux inscriptions simultanées passant toutes les deux la
        // vérification applicative avant que l'une des deux ne déclenche
        // la contrainte UNIQUE(lawyer_id, client_id) en base.
        when(waitlistRepository.findByLawyerIdAndClientId(LAWYER_ID, CLIENT_ID)).thenReturn(Optional.empty());
        when(waitlistRepository.save(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("contrainte UNIQUE violée"));

        assertThatThrownBy(() -> waitlistService.joinWaitlist(LAWYER_ID, CLIENT_ID))
                .isInstanceOf(AlreadyOnWaitlistException.class);
    }

    @Test
    void joinWaitlist_allowsDifferentClients_onSameLawyer() {
        when(waitlistRepository.findByLawyerIdAndClientId(eq(LAWYER_ID), any())).thenReturn(Optional.empty());
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        waitlistService.joinWaitlist(LAWYER_ID, 1L);
        waitlistService.joinWaitlist(LAWYER_ID, 2L);

        verify(waitlistRepository, times(2)).save(any(WaitlistEntry.class));
    }

    // ══════════════════════════════════════════════════════════
    //  getWaitlist
    // ══════════════════════════════════════════════════════════
    @Test
    void getWaitlist_returnsEmptyList_whenNoEntries() {
        when(waitlistRepository.findByLawyerIdOrderByCreatedAtAsc(LAWYER_ID)).thenReturn(List.of());

        List<WaitlistEntryResponse> result = waitlistService.getWaitlist(LAWYER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getWaitlist_returnsEntries_inRepositoryOrder_earliestFirst() {
        WaitlistEntry first = buildEntry(1L, 10L, LocalDateTime.now().minusHours(2));
        WaitlistEntry second = buildEntry(2L, 20L, LocalDateTime.now().minusHours(1));
        when(waitlistRepository.findByLawyerIdOrderByCreatedAtAsc(LAWYER_ID))
                .thenReturn(List.of(first, second));

        List<WaitlistEntryResponse> result = waitlistService.getWaitlist(LAWYER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getClientId()).isEqualTo(10L);
        assertThat(result.get(1).getClientId()).isEqualTo(20L);
    }

    @Test
    void getWaitlist_doesNotLeakOtherLawyersEntries() {
        // Vérifie que le service délègue bien le filtrage par lawyerId au
        // repository plutôt que de tout charger et filtrer en mémoire.
        waitlistService.getWaitlist(LAWYER_ID);

        verify(waitlistRepository).findByLawyerIdOrderByCreatedAtAsc(LAWYER_ID);
        verify(waitlistRepository, never()).findAll();
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════
    private WaitlistEntry buildEntry(Long id, Long clientId, LocalDateTime createdAt) {
        WaitlistEntry e = new WaitlistEntry();
        e.setId(id);
        e.setLawyerId(LAWYER_ID);
        e.setClientId(clientId);
        e.setCreatedAt(createdAt);
        return e;
    }
}