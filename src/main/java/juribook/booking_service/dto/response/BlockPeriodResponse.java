package juribook.booking_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Résultat d'un blocage de période (congés/indisponibilité).
 * Retourné par POST /api/lawyers/{lawyerId}/slots/block.
 */
@Data
@Builder
public class BlockPeriodResponse {

    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
    private int blockedSlotsCount;
    private List<TimeSlotResponse> blockedSlots;
}
