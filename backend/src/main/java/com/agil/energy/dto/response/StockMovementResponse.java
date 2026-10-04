package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementResponse {

    private Long id;
    private Long tankId;
    private String fuelTypeName;
    private String stationName;
    private String movementType;
    private BigDecimal quantity;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private String justification;
    private String createdByName;
    private LocalDateTime createdAt;
}