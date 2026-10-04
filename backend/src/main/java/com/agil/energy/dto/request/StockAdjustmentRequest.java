package com.agil.energy.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustmentRequest {

    @NotNull(message = "L'ID du réservoir est obligatoire")
    private Long tankId;

    @NotNull(message = "La nouvelle quantité est obligatoire")
    @DecimalMin(value = "0.00", inclusive = true, message = "Le stock ne peut pas être négatif")
    private BigDecimal newStock;

    @NotBlank(message = "La justification est obligatoire")
    private String justification;
}