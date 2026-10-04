package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateSaleRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.SaleResponse;
import com.agil.energy.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<ApiResponse<SaleResponse>> createSale(
            @Valid @RequestBody CreateSaleRequest request) {

        SaleResponse response = saleService.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vente enregistrée avec succès", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleResponse>> getSaleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(saleService.getSaleById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleResponse>>> getSales(
            @RequestParam Long stationId,
            @RequestParam(required = false) Long fuelTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<SaleResponse> sales;

        if (startDate != null && endDate != null && fuelTypeId != null) {
            sales = saleService.getSalesByStationFuelAndDateRange(stationId, fuelTypeId, startDate, endDate);
        } else if (startDate != null && endDate != null) {
            sales = saleService.getSalesByStationAndDateRange(stationId, startDate, endDate);
        } else {
            sales = saleService.getSalesByStation(stationId);
        }

        return ResponseEntity.ok(ApiResponse.success(sales));
    }

    @PutMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<SaleResponse>> validateSale(@PathVariable Long id) {
        SaleResponse response = saleService.validateSale(id);
        return ResponseEntity.ok(ApiResponse.success("Vente validée", response));
    }
}