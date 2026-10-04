package com.agil.energy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStationRequest {

    @NotBlank(message = "Le nom de la station est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    @NotBlank(message = "La région est obligatoire")
    @Size(max = 100, message = "La région ne doit pas dépasser 100 caractères")
    private String region;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Long managerId;
}