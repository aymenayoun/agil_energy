package com.agil.energy.service.impl;

import com.agil.energy.dto.request.CreateFuelTypeRequest;
import com.agil.energy.dto.request.UpdateFuelTypeRequest;
import com.agil.energy.entity.FuelType;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.repository.FuelTypeRepository;
import com.agil.energy.repository.TankRepository;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.FuelTypeService;
import com.agil.energy.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FuelTypeServiceImpl implements FuelTypeService {

    private final FuelTypeRepository fuelTypeRepository;
    private final TankRepository tankRepository;
    private final AuditService auditService;

    @Override
    public FuelType createFuelType(CreateFuelTypeRequest request) {
        if (fuelTypeRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException(
                    "Un type de carburant nommé '" + request.getName() + "' existe déjà");
        }

        FuelType fuelType = FuelType.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        FuelType saved = fuelTypeRepository.save(fuelType);

        auditService.log(null, "CREATE_FUEL_TYPE", "FuelType", saved.getId(),
                String.format("{\"name\":\"%s\"}", saved.getName()), null);

        return saved;
    }

    @Override
    public FuelType updateFuelType(Long id, UpdateFuelTypeRequest request) {
        FuelType fuelType = fuelTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", id));

        if (request.getName() != null && !request.getName().equals(fuelType.getName())) {
            fuelTypeRepository.findByName(request.getName())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException(
                                "Un type de carburant nommé '" + request.getName() + "' existe déjà");
                    });
            fuelType.setName(request.getName());
        }

        if (request.getDescription() != null) {
            fuelType.setDescription(request.getDescription());
        }

        FuelType saved = fuelTypeRepository.save(fuelType);

        auditService.log(null, "UPDATE_FUEL_TYPE", "FuelType", saved.getId(),
                String.format("{\"name\":\"%s\"}", saved.getName()), null);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public FuelType getFuelTypeById(Long id) {
        return fuelTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FuelType> getAllFuelTypes() {
        return fuelTypeRepository.findAll();
    }
    
    @Override
    public void deleteFuelType(Long id) {
        FuelType fuelType = fuelTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", id));

        if (tankRepository.existsByFuelTypeId(id)) {
            throw new BusinessException(
                    "Ce type de carburant est utilisé par un ou plusieurs réservoirs et ne peut pas être supprimé");
        }

        fuelTypeRepository.delete(fuelType);
        auditService.log(null, "DELETE_FUEL_TYPE", "FuelType", id, null, null);
    }
}