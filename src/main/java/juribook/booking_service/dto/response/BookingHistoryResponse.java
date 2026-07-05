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
 * Version enrichie de BookingResponse pour l'historique client,
 * le tableau de bord avocat, et la résolution inter-services
 * : ajoute la date/heure du créneau, résolues en joignant
 * TimeSlot côté service, Booking ne stocke que timeSlotId, pas la
 * date/heure elle-même.
 *
 * clientId  : absent jusqu'ici, aucun consommateur
 * inter-services n'en avait besoin (notification-service le reçoit
 * déjà directement via l'événement Kafka). lawyer-service en a
 * désormais besoin pour vérifier, à la création d'un avis, que la
 * réservation appartient bien au client qui le poste.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingHistoryResponse {

    private Long id;
    private Long clientId;
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
                .clientId(booking.getClientId())
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