package juribook.booking_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingResponse {

    private Long id;
    private Long clientId;
    private Long lawyerId;
    private Long timeSlotId;
    private BookingStatus status;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BookingResponse from(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .clientId(b.getClientId())
                .lawyerId(b.getLawyerId())
                .timeSlotId(b.getTimeSlotId())
                .status(b.getStatus())
                .reason(b.getReason())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}