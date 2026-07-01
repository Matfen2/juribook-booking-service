package juribook.booking_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import juribook.booking_service.entity.WaitlistEntry;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WaitlistEntryResponse {

    private Long id;
    private Long lawyerId;
    private Long clientId;
    private LocalDateTime createdAt;

    public static WaitlistEntryResponse from(WaitlistEntry entry) {
        return WaitlistEntryResponse.builder()
                .id(entry.getId())
                .lawyerId(entry.getLawyerId())
                .clientId(entry.getClientId())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}