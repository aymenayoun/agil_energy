package com.agil.energy.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryRequest {

    @NotNull(message = "L'ID de la station est obligatoire")
    private Long stationId;

    @NotNull(message = "L'ID du type de carburant est obligatoire")
    private Long fuelTypeId;

    @NotNull(message = "La date de livraison est obligatoire")
    private LocalDate deliveryDate;

    @NotNull(message = "La quantité est obligatoire")
    @DecimalMin(value = "0.01", message = "La quantité doit être positive")
    private BigDecimal quantity;
}