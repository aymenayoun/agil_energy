package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateStationRequest;
import com.agil.energy.dto.request.CreateTankRequest;
import com.agil.energy.dto.request.UpdateStationRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.StationResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.StationService;
import com.agil.energy.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;
    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StationResponse>> createStation(
            @Valid @RequestBody CreateStationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        StationResponse response = stationService.createStation(request);
        auditService.log(currentUser.getId(), "CREATE", "STATION", response.getId(),
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Station créée avec succès", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StationResponse>>> getAllStations(
            @RequestParam(required = false) String region) {

        List<StationResponse> stations = region != null
                ? stationService.getStationsByRegion(region)
                : stationService.getAllStations();
        return ResponseEntity.ok(ApiResponse.success(stations));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StationResponse>> getStationById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(stationService.getStationById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StationResponse>> updateStation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        StationResponse response = stationService.updateStation(id, request);
        auditService.log(currentUser.getId(), "UPDATE", "STATION", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Station mise à jour", response));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateStation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        stationService.deactivateStation(id);
        auditService.log(currentUser.getId(), "DEACTIVATE", "STATION", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Station désactivée", null));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateStation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        stationService.activateStation(id);
        auditService.log(currentUser.getId(), "ACTIVATE", "STATION", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Station activée", null));
    }

    @PostMapping("/tanks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TankResponse>> addTank(
            @Valid @RequestBody CreateTankRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        TankResponse response = stationService.addTank(request);
        auditService.log(currentUser.getId(), "CREATE", "TANK", response.getId(),
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Réservoir ajouté avec succès", response));
    }
}