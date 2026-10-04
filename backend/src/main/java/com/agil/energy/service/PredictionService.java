package com.agil.energy.service;

import com.agil.energy.dto.response.PredictionResponse;

import java.util.List;

public interface PredictionService {

    List<PredictionResponse> getPredictions(Long stationId, Long fuelTypeId);

    List<PredictionResponse> getLatestPredictions(Long stationId, Long fuelTypeId);
}