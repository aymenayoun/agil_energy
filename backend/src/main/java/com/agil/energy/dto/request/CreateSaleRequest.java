package com.agil.energy.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSaleRequest {

    @NotNull(message = "L'ID de la station est obligatoire")
    private Long stationId;

    @NotNull(message = "L'ID du type de carburant est obligatoire")
    private Long fuelTypeId;

    @NotNull(message = "La date de vente est obligatoire")
    @PastOrPresent(message = "La date de vente ne peut pas être dans le futur")
    private LocalDate saleDate;

    @NotNull(message = "La quantité est obligatoire")
    @DecimalMin(value = "0.00", inclusive = true, message = "La quantité ne peut pas être négative")
    private BigDecimal quantity;
}