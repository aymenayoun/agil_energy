package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateFuelTypeRequest;
import com.agil.energy.dto.request.UpdateFuelTypeRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.entity.FuelType;
import com.agil.energy.service.FuelTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fuel-types")
@RequiredArgsConstructor
public class FuelTypeController {

    private final FuelTypeService fuelTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FuelType>>> getAllFuelTypes() {
        return ResponseEntity.ok(ApiResponse.success(fuelTypeService.getAllFuelTypes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FuelType>> getFuelTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fuelTypeService.getFuelTypeById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FuelType>> createFuelType(
            @Valid @RequestBody CreateFuelTypeRequest request) {

        FuelType response = fuelTypeService.createFuelType(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Type de carburant créé avec succès", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FuelType>> updateFuelType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFuelTypeRequest request) {

        FuelType response = fuelTypeService.updateFuelType(id, request);
        return ResponseEntity.ok(ApiResponse.success("Type de carburant mis à jour", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFuelType(@PathVariable Long id) {
        fuelTypeService.deleteFuelType(id);
        return ResponseEntity.ok(ApiResponse.success("Type de carburant supprimé", null));
    }
}