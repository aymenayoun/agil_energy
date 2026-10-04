package com.agil.energy.service;

import com.agil.energy.dto.request.CreateFuelTypeRequest;
import com.agil.energy.dto.request.UpdateFuelTypeRequest;
import com.agil.energy.entity.FuelType;

import java.util.List;

public interface FuelTypeService {

    FuelType createFuelType(CreateFuelTypeRequest request);

    FuelType updateFuelType(Long id, UpdateFuelTypeRequest request);

    FuelType getFuelTypeById(Long id);

    List<FuelType> getAllFuelTypes();

    void deleteFuelType(Long id);
}