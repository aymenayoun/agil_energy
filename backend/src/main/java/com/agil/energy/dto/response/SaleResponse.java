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
public class SaleResponse {

    private Long id;
    private Long stationId;
    private String stationName;
    private Long fuelTypeId;
    private String fuelTypeName;
    private LocalDate saleDate;
    private BigDecimal quantity;
    private Boolean validated;
    private LocalDateTime createdAt;
}