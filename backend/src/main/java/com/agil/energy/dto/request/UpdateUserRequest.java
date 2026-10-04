package com.agil.energy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String name;

    @Email(message = "Format email invalide")
    private String email;

    private String roleName;

    /**
     * Optional admin override to re-assign a STATION_MANAGER to a different station.
     * Service layer enforces role consistency.
     */
    private Long stationId;

    /**
     * Optional admin override to re-assign a MANAGER to a different region.
     * Service layer enforces role consistency.
     */
    private String region;
}