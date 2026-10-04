package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateDeliveryRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.DeliveryResponse;
import com.agil.energy.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryResponse>> createDelivery(
            @Valid @RequestBody CreateDeliveryRequest request) {

        DeliveryResponse response = deliveryService.createDelivery(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Livraison enregistrée avec succès", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getDeliveries(
            @RequestParam(required = false) Long stationId) {

        List<DeliveryResponse> deliveries = stationId != null
                ? deliveryService.getDeliveriesByStation(stationId)
                : deliveryService.getAllDeliveries();
        return ResponseEntity.ok(ApiResponse.success(deliveries));
    }

    @PutMapping("/{id}/validate")
    public ResponseEntity<ApiResponse<DeliveryResponse>> validateDelivery(@PathVariable Long id) {
        DeliveryResponse response = deliveryService.validateDelivery(id);
        return ResponseEntity.ok(ApiResponse.success("Livraison validée", response));
    }
}