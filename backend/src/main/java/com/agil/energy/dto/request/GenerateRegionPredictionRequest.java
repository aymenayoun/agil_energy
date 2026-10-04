package com.agil.energy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateRegionPredictionRequest {

    @NotBlank(message = "La région est obligatoire")
    private String region;

    @NotNull(message = "L'ID du type de carburant est obligatoire")
    private Long fuelTypeId;
}