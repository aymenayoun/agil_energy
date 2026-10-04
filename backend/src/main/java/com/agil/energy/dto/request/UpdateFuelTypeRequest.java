package com.agil.energy.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFuelTypeRequest {

    @Size(max = 50, message = "Le nom ne peut pas dépasser 50 caractères")
    private String name;

    @Size(max = 255, message = "La description ne peut pas dépasser 255 caractères")
    private String description;
}