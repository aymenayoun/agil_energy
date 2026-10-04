package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TankResponse {

    private Long id;
    private Long stationId;
    private String stationName;
    private Long fuelTypeId;
    private String fuelTypeName;
    private BigDecimal capacity;
    private BigDecimal currentStock;
    private BigDecimal criticalThreshold;
    private BigDecimal stockPercentage;
    private boolean critical;
}