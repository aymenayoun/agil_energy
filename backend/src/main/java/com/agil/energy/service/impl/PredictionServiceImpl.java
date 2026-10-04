package com.agil.energy.service.impl;

import com.agil.energy.dto.response.PredictionResponse;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.PredictionRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionServiceImpl implements PredictionService {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private final PredictionRepository predictionRepository;
    private final EntityMapper mapper;
    private final CurrentUserContext ctx;

    @Override
    public List<PredictionResponse> getPredictions(Long stationId, Long fuelTypeId) {
        ctx.requireAccessTo(stationId);
        LocalDate today = LocalDate.now(TUNIS);
        LocalDate endDate = today.plusDays(14);
        return predictionRepository
                .findByStationIdAndFuelTypeIdAndPredictionDateBetweenOrderByPredictionDateAsc(
                        stationId, fuelTypeId, today, endDate)
                .stream()
                .map(mapper::toPredictionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PredictionResponse> getLatestPredictions(Long stationId, Long fuelTypeId) {
        ctx.requireAccessTo(stationId);
        return predictionRepository.findLatestPredictions(stationId, fuelTypeId).stream()
                .map(mapper::toPredictionResponse)
                .collect(Collectors.toList());
    }
}