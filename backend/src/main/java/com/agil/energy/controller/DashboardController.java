package com.agil.energy.controller;

import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.DashboardResponse;
import com.agil.energy.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) Long stationId) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getDashboard(stationId)));
    }
}