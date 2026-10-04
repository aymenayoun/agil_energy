package com.agil.energy.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String roleName;
    private String status;
    private Long stationId;       // non-null for STATION_MANAGER
    private String stationName;   // non-null for STATION_MANAGER
    private String region;        // non-null for MANAGER
    private LocalDateTime createdAt;
}