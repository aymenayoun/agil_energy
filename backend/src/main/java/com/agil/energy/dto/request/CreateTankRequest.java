package com.agil.energy.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTankRequest {

    @NotNull(message = "L'ID de la station est obligatoire")
    private Long stationId;

    @NotNull(message = "L'ID du type de carburant est obligatoire")
    private Long fuelTypeId;

    @NotNull(message = "La capacité est obligatoire")
    @DecimalMin(value = "0.01", message = "La capacité doit être positive")
    private BigDecimal capacity;

    @NotNull(message = "Le stock initial est obligatoire")
    @DecimalMin(value = "0.00", inclusive = true, message = "Le stock ne peut pas être négatif")
    private BigDecimal currentStock;

    @NotNull(message = "Le seuil critique est obligatoire")
    @DecimalMin(value = "0.00", inclusive = true, message = "Le seuil critique ne peut pas être négatif")
    private BigDecimal criticalThreshold;
}