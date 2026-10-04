package com.agil.energy.controller;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AlertService;
import com.agil.energy.enums.AlertType;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.agil.energy.dto.response.AnomalyStatsResponse;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STATION_MANAGER')")
    public ResponseEntity<ApiResponse<Page<AlertResponse>>> getActiveAlerts(
            @RequestParam(required = false) AlertType alertType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(alertService.getActiveAlerts(alertType, pageable)));
    }

    @GetMapping("/station/{stationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STATION_MANAGER')")
    public ResponseEntity<ApiResponse<Page<AlertResponse>>> getAlertsByStation(
            @PathVariable Long stationId,
            @RequestParam(required = false) AlertType alertType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(alertService.getAlertsByStation(stationId, alertType, pageable)));
    }

    @PutMapping("/{alertId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STATION_MANAGER')")
    public ResponseEntity<ApiResponse<AlertResponse>> resolveAlert(
            @PathVariable Long alertId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(ApiResponse.success(
                alertService.resolveAlert(alertId, userDetails.getId())));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STATION_MANAGER')")
    public ResponseEntity<ApiResponse<Long>> countActiveAlerts() {
        return ResponseEntity.ok(ApiResponse.success(alertService.countActiveAlerts()));
    }

    @GetMapping("/stats/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STATION_MANAGER')")
    public ResponseEntity<ApiResponse<AnomalyStatsResponse>> getAnomalyStats() {
        return ResponseEntity.ok(ApiResponse.success(alertService.getAnomalyStats()));
    }
}