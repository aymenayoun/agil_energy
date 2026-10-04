package com.agil.energy.service.impl;

import com.agil.energy.dto.request.CreateSaleRequest;
import com.agil.energy.dto.response.SaleResponse;
import com.agil.energy.entity.*;
import com.agil.energy.enums.MovementType;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.AlertCheckService;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.SaleAnomalyDetector;
import com.agil.energy.service.SaleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final StationRepository stationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final TankRepository tankRepository;
    private final StockMovementRepository stockMovementRepository;
    private final EntityMapper mapper;
    private final AlertCheckService alertCheckService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SaleAnomalyDetector saleAnomalyDetector;
    private final CurrentUserContext ctx;

    @Override
    public SaleResponse createSale(CreateSaleRequest request) {
        ctx.requireAccessTo(request.getStationId());

        if (request.getSaleDate().isAfter(LocalDate.now())) {
            throw new BusinessException("La date de vente ne peut pas être dans le futur");
        }

        if (saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(
                request.getStationId(), request.getFuelTypeId(), request.getSaleDate())) {
            throw new DuplicateResourceException(
                    "Une vente existe déjà pour cette station, ce carburant et cette date");
        }

        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", request.getStationId()));

        FuelType fuelType = fuelTypeRepository.findById(request.getFuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", request.getFuelTypeId()));

        Tank tank = tankRepository.findByStationIdAndFuelTypeIdForUpdate(request.getStationId(), request.getFuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Réservoir non trouvé pour la station " + request.getStationId()
                                + " et le carburant " + request.getFuelTypeId()));

        if (tank.getCurrentStock().compareTo(request.getQuantity()) < 0) {
            throw new BusinessException("Stock insuffisant. Stock actuel: " + tank.getCurrentStock()
                    + "L, Quantité demandée: " + request.getQuantity() + "L");
        }

        Sale sale = Sale.builder()
                .station(station)
                .fuelType(fuelType)
                .saleDate(request.getSaleDate())
                .quantity(request.getQuantity())
                .validated(false)
                .build();
        sale = saleRepository.save(sale);

        BigDecimal stockBefore = tank.getCurrentStock();
        BigDecimal stockAfter = stockBefore.subtract(request.getQuantity());
        tank.setCurrentStock(stockAfter);
        tankRepository.save(tank);

        StockMovement movement = StockMovement.builder()
                .tank(tank)
                .movementType(MovementType.SALE)
                .quantity(request.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceId(sale.getId())
                .build();
        stockMovementRepository.save(movement);

        alertCheckService.checkStockAlerts(request.getStationId(), request.getFuelTypeId());
        saleAnomalyDetector.detect(sale);

        Long currentUserId = ctx.currentUserId();
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "station", request.getStationId(),
                    "fuel",    request.getFuelTypeId(),
                    "qty",     request.getQuantity(),
                    "date",    request.getSaleDate()));
            auditService.log(currentUserId, "CREATE_SALE", "Sale", sale.getId(), payload, null);
        } catch (JsonProcessingException e) {
            // swallow – audit must not break the business op
        }

        return mapper.toSaleResponse(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getSaleById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vente", id));
        ctx.requireAccessTo(sale.getStation().getId());
        return mapper.toSaleResponse(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByStation(Long stationId) {
        ctx.requireAccessTo(stationId);
        return saleRepository.findByStationIdOrderBySaleDateDesc(stationId).stream()
                .map(mapper::toSaleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByStationAndDateRange(Long stationId, LocalDate startDate, LocalDate endDate) {
        ctx.requireAccessTo(stationId);
        return saleRepository.findByStationIdAndSaleDateBetweenOrderBySaleDateAsc(stationId, startDate, endDate)
                .stream()
                .map(mapper::toSaleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByStationFuelAndDateRange(Long stationId, Long fuelTypeId,
                                                                LocalDate startDate, LocalDate endDate) {
        ctx.requireAccessTo(stationId);
        return saleRepository.findByStationIdAndFuelTypeIdAndSaleDateBetweenOrderBySaleDateAsc(
                        stationId, fuelTypeId, startDate, endDate)
                .stream()
                .map(mapper::toSaleResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SaleResponse validateSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vente", id));
        ctx.requireAccessTo(sale.getStation().getId());

        if (sale.getValidated()) {
            throw new BusinessException("Cette vente est déjà validée");
        }

        sale.setValidated(true);
        Long currentUserId = ctx.currentUserId();
        auditService.log(currentUserId, "VALIDATE_SALE", "Sale", id, null, null);
        return mapper.toSaleResponse(saleRepository.save(sale));
    }
}