package com.agil.energy.service;

import com.agil.energy.dto.request.CreateStationRequest;
import com.agil.energy.dto.request.CreateTankRequest;
import com.agil.energy.dto.request.UpdateStationRequest;
import com.agil.energy.dto.response.StationResponse;
import com.agil.energy.dto.response.TankResponse;

import java.util.List;

public interface StationService {

    StationResponse createStation(CreateStationRequest request);

    StationResponse updateStation(Long id, UpdateStationRequest request);

    StationResponse getStationById(Long id);

    List<StationResponse> getAllStations();

    List<StationResponse> getStationsByRegion(String region);

    void deactivateStation(Long id);

    void activateStation(Long id);

    TankResponse addTank(CreateTankRequest request);
}