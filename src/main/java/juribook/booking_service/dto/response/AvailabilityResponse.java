package juribook.booking_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.booking_service.entity.Availability;
import lombok.Builder;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvailabilityResponse {

    private Long id;
    private Long lawyerId;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotDurationMinutes;
    private boolean active;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Nombre de créneaux concrets générés lors de la création
    // (informatif, présent uniquement dans la réponse de création)
    private Integer generatedSlotsCount;

    public static AvailabilityResponse from(Availability a) {
        return AvailabilityResponse.builder()
                .id(a.getId())
                .lawyerId(a.getLawyerId())
                .dayOfWeek(a.getDayOfWeek())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .slotDurationMinutes(a.getSlotDurationMinutes())
                .active(a.isActive())
                .validFrom(a.getValidFrom())
                .validUntil(a.getValidUntil())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    public static AvailabilityResponse from(Availability a, int generatedSlotsCount) {
        AvailabilityResponse response = from(a);
        response.setGeneratedSlotsCount(generatedSlotsCount);
        return response;
    }
}
