package com.agil.energy.dto.response;

import com.agil.energy.entity.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertEvent {
    private Long id;
    private Long stationId;
    private String stationName;
    private String alertType;
    private String severity;
    private String message;
    private String status;
    private LocalDateTime createdAt;

    public static AlertEvent from(Alert a) {
        return AlertEvent.builder()
                .id(a.getId())
                .stationId(a.getStation().getId())
                .stationName(a.getStation().getName())
                .alertType(a.getAlertType().name())
                .severity(a.getSeverity().name())
                .message(a.getMessage())
                .status(a.getStatus().name())
                .createdAt(a.getCreatedAt())
                .build();
    }
}