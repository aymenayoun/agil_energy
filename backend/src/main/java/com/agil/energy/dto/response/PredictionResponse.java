package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResponse {

    private Long id;
    private Long stationId;
    private String stationName;
    private Long fuelTypeId;
    private String fuelTypeName;
    private LocalDate predictionDate;
    private BigDecimal predictedQuantity;
    private String modelName;
    private BigDecimal confidenceScore;
    private LocalDateTime generatedAt;
}