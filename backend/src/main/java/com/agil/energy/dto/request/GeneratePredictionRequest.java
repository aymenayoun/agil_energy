package com.agil.energy.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratePredictionRequest {

    @NotNull(message = "L'ID de la station est obligatoire")
    private Long stationId;

    @NotNull(message = "L'ID du type de carburant est obligatoire")
    private Long fuelTypeId;
}