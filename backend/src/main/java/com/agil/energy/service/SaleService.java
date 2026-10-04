package com.agil.energy.service;

import com.agil.energy.dto.request.CreateSaleRequest;
import com.agil.energy.dto.response.SaleResponse;

import java.time.LocalDate;
import java.util.List;

public interface SaleService {

    SaleResponse createSale(CreateSaleRequest request);

    SaleResponse getSaleById(Long id);

    List<SaleResponse> getSalesByStation(Long stationId);

    List<SaleResponse> getSalesByStationAndDateRange(Long stationId, LocalDate startDate, LocalDate endDate);

    List<SaleResponse> getSalesByStationFuelAndDateRange(Long stationId, Long fuelTypeId, LocalDate startDate, LocalDate endDate);

    SaleResponse validateSale(Long id);
}