package com.agil.energy.controller;

import com.agil.energy.dto.request.GeneratePredictionRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.PredictionResponse;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.IAClientService;
import com.agil.energy.service.PredictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.agil.energy.dto.request.GenerateRegionPredictionRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class PredictionController {

    private final PredictionService predictionService;
    private final IAClientService iaClientService;
    private final CurrentUserContext ctx;

    @GetMapping("/{stationId}")
    public ResponseEntity<ApiResponse<List<PredictionResponse>>> getPredictions(
            @PathVariable Long stationId,
            @RequestParam Long fuelTypeId) {

        // requireAccessTo checks region for MANAGER
        ctx.requireAccessTo(stationId);
        List<PredictionResponse> predictions = predictionService.getPredictions(stationId, fuelTypeId);
        return ResponseEntity.ok(ApiResponse.success(predictions));
    }

    @GetMapping("/{stationId}/latest")
    public ResponseEntity<ApiResponse<List<PredictionResponse>>> getLatestPredictions(
            @PathVariable Long stationId,
            @RequestParam Long fuelTypeId) {

        ctx.requireAccessTo(stationId);
        List<PredictionResponse> predictions = predictionService.getLatestPredictions(stationId, fuelTypeId);
        return ResponseEntity.ok(ApiResponse.success(predictions));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generatePrediction(
            @Valid @RequestBody GeneratePredictionRequest request) {

        // MANAGER can only generate predictions for stations in their region
        ctx.requireAccessTo(request.getStationId());

        Map<String, Object> result = iaClientService.generatePrediction(
                request.getStationId(), request.getFuelTypeId(), 7);
        return ResponseEntity.ok(ApiResponse.success("Prévisions générées", result));
    }

    @PostMapping("/generate/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateBatchPredictions() {

        Map<String, Object> result = iaClientService.generateBatchPredictions(7);
        return ResponseEntity.ok(ApiResponse.success("Prédictions batch terminées", result));
    }

    @GetMapping("/ia/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkIAHealth() {
        boolean healthy = iaClientService.isHealthy();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "ia_service", healthy ? "available" : "unavailable"
        )));
    }

    @GetMapping("/model-metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getModelMetrics(
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) Long fuelTypeId) {

        // If MANAGER requests metrics for a specific station, validate access
        if (stationId != null) {
            ctx.requireAccessTo(stationId);
        }
        Map<String, Object> metrics = iaClientService.getModelMetrics(stationId, fuelTypeId);
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    // ===== Region-based Prediction Endpoints =====

    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRegions() {
        // MANAGER: only their own region; ADMIN: all
        String scopedRegion = ctx.scopedRegion();
        if (scopedRegion != null) {
            // Return a single-element list with just the manager's region
            List<Map<String, Object>> filtered = iaClientService.getRegions().stream()
                    .filter(r -> scopedRegion.equals(r.get("region")))
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(filtered));
        }
        List<Map<String, Object>> regions = iaClientService.getRegions();
        return ResponseEntity.ok(ApiResponse.success(regions));
    }

    @GetMapping("/regions/{region}/fuel-types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRegionFuelTypes(
            @PathVariable String region) {

        // MANAGER can only access their own region
        requireRegionAccess(region);
        List<Map<String, Object>> fuelTypes = iaClientService.getRegionFuelTypes(region);
        return ResponseEntity.ok(ApiResponse.success(fuelTypes));
    }

    @PostMapping("/generate/region")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateRegionPrediction(
            @Valid @RequestBody GenerateRegionPredictionRequest request) {

        // MANAGER can only generate predictions for their own region
        requireRegionAccess(request.getRegion());

        Map<String, Object> result = iaClientService.generateRegionPrediction(
                request.getRegion(), request.getFuelTypeId(), 7);
        return ResponseEntity.ok(ApiResponse.success("Prévisions régionales générées", result));
    }

    // ---------- helpers ----------

    /**
     * Ensures a MANAGER can only access their own assigned region.
     * ADMIN has no restriction.
     */
    private void requireRegionAccess(String region) {
        String scopedRegion = ctx.scopedRegion();
        if (scopedRegion != null && !scopedRegion.equals(region)) {
            throw new AccessDeniedException(
                    "Accès refusé: vous ne pouvez accéder qu'à la région " + scopedRegion);
        }
    }
}