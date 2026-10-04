package com.agil.energy.service;

import com.agil.energy.dto.request.StockAdjustmentRequest;
import com.agil.energy.dto.response.StockMovementResponse;
import com.agil.energy.dto.response.TankResponse;

import java.util.List;

public interface StockService {

    List<TankResponse> getStocksByStation(Long stationId);

    TankResponse adjustStock(StockAdjustmentRequest request, Long userId);

    List<TankResponse> getCriticalTanks();

    List<StockMovementResponse> getMovementsByTank(Long tankId);
}