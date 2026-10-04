package com.agil.energy.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    private String name;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @NotBlank(message = "Le rôle est obligatoire")
    private String roleName;

    /**
     * Required when roleName = STATION_MANAGER, otherwise must be null.
     * Validated at the service layer (cross-field rule).
     */
    private Long stationId;

    /**
     * Required when roleName = MANAGER, otherwise must be null.
     * Must match an existing station region value.
     * Validated at the service layer (cross-field rule).
     */
    private String region;
}