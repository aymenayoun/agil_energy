package com.agil.energy.controller;

import com.agil.energy.dto.request.StockAdjustmentRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.StockMovementResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/{stationId}")
    public ResponseEntity<ApiResponse<List<TankResponse>>> getStocksByStation(
            @PathVariable Long stationId) {
        return ResponseEntity.ok(ApiResponse.success(stockService.getStocksByStation(stationId)));
    }

    @PutMapping("/adjust")
    public ResponseEntity<ApiResponse<TankResponse>> adjustStock(
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        TankResponse response = stockService.adjustStock(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Stock ajusté avec succès", response));
    }

    @GetMapping("/critical")
    public ResponseEntity<ApiResponse<List<TankResponse>>> getCriticalTanks() {
        return ResponseEntity.ok(ApiResponse.success(stockService.getCriticalTanks()));
    }

    @GetMapping("/movements/{tankId}")
    public ResponseEntity<ApiResponse<List<StockMovementResponse>>> getMovements(
            @PathVariable Long tankId) {
        return ResponseEntity.ok(ApiResponse.success(stockService.getMovementsByTank(tankId)));
    }
}