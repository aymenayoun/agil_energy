package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationResponse {

    private Long id;
    private String name;
    private String region;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
    /** Derived: id of the active STATION_MANAGER user attached to this station, or null. */
    private Long managerId;
    /** Derived: name of the active STATION_MANAGER user attached to this station, or null. */
    private String managerName;
    private List<TankResponse> tanks;
    private LocalDateTime createdAt;
}