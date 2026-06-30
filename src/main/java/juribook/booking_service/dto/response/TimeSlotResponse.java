package juribook.booking_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.booking_service.entity.SlotStatus;
import juribook.booking_service.entity.TimeSlot;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeSlotResponse {

    private Long id;
    private Long lawyerId;
    private Long availabilityId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private SlotStatus status;

    public static TimeSlotResponse from(TimeSlot t) {
        return TimeSlotResponse.builder()
                .id(t.getId())
                .lawyerId(t.getLawyerId())
                .availabilityId(t.getAvailabilityId())
                .date(t.getDate())
                .startTime(t.getStartTime())
                .endTime(t.getEndTime())
                .status(t.getStatus())
                .build();
    }
}
