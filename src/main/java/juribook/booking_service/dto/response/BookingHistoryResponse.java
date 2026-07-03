package juribook.booking_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.booking_service.entity.Booking;
import juribook.booking_service.entity.BookingStatus;
import juribook.booking_service.entity.TimeSlot;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Version enrichie de BookingResponse pour l'historique client
 * : ajoute la date/heure du créneau, résolues en joignant
 * TimeSlot côté service (même base de données, pas d'appel inter-services
 * nécessaire). Booking ne stocke que timeSlotId, pas la date/heure
 * elle-même.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingHistoryResponse {

    private Long id;
    private Long lawyerId;
    private Long timeSlotId;
    private BookingStatus status;
    private String reason;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime createdAt;

    public static BookingHistoryResponse from(Booking booking, TimeSlot slot) {
        return BookingHistoryResponse.builder()
                .id(booking.getId())
                .lawyerId(booking.getLawyerId())
                .timeSlotId(booking.getTimeSlotId())
                .status(booking.getStatus())
                .reason(booking.getReason())
                .date(slot != null ? slot.getDate() : null)
                .startTime(slot != null ? slot.getStartTime() : null)
                .endTime(slot != null ? slot.getEndTime() : null)
                .createdAt(booking.getCreatedAt())
                .build();
    }
}