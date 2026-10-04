package com.agil.energy.service.impl;

import com.agil.energy.dto.request.CreateDeliveryRequest;
import com.agil.energy.dto.response.DeliveryResponse;
import com.agil.energy.entity.*;
import com.agil.energy.enums.MovementType;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.AlertCheckService;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.DeliveryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final StationRepository stationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final TankRepository tankRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final EntityMapper mapper;
    private final AlertCheckService alertCheckService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CurrentUserContext ctx;

    @Override
    public DeliveryResponse createDelivery(CreateDeliveryRequest request) {
        ctx.requireAccessTo(request.getStationId());

        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", request.getStationId()));

        FuelType fuelType = fuelTypeRepository.findById(request.getFuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", request.getFuelTypeId()));

        Tank tank = tankRepository.findByStationIdAndFuelTypeIdForUpdate(request.getStationId(), request.getFuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Réservoir non trouvé pour la station " + request.getStationId()
                                + " et le carburant " + request.getFuelTypeId()));

        BigDecimal newStock = tank.getCurrentStock().add(request.getQuantity());
        if (newStock.compareTo(tank.getCapacity()) > 0) {
            throw new BusinessException("La livraison dépasse la capacité du réservoir. Capacité: "
                    + tank.getCapacity() + "L, Stock actuel: " + tank.getCurrentStock()
                    + "L, Livraison: " + request.getQuantity() + "L");
        }

        User currentUser = ctx.currentUserId() != null
                ? userRepository.findById(ctx.currentUserId()).orElse(null)
                : null;

        Delivery delivery = Delivery.builder()
                .station(station)
                .fuelType(fuelType)
                .deliveryDate(request.getDeliveryDate())
                .quantity(request.getQuantity())
                .validated(false)
                .createdBy(currentUser)
                .build();
        delivery = deliveryRepository.save(delivery);

        BigDecimal stockBefore = tank.getCurrentStock();
        tank.setCurrentStock(newStock);
        tankRepository.save(tank);

        StockMovement movement = StockMovement.builder()
                .tank(tank)
                .movementType(MovementType.DELIVERY)
                .quantity(request.getQuantity())
                .stockBefore(stockBefore)
                .stockAfter(newStock)
                .referenceId(delivery.getId())
                .createdBy(currentUser)
                .build();
        stockMovementRepository.save(movement);

        alertCheckService.checkStockAlerts(request.getStationId(), request.getFuelTypeId());

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "station", request.getStationId(),
                    "fuel",    request.getFuelTypeId(),
                    "qty",     request.getQuantity(),
                    "date",    request.getDeliveryDate()));

            auditService.log(
                    currentUser != null ? currentUser.getId() : null,
                    "CREATE_DELIVERY",
                    "Delivery",
                    delivery.getId(),
                    payload,
                    null);
        } catch (JsonProcessingException e) {
            // swallow
        }

        return mapper.toDeliveryResponse(delivery);
    }

    @Override
    public DeliveryResponse validateDelivery(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison", id));
        ctx.requireAccessTo(delivery.getStation().getId());

        if (delivery.getValidated()) {
            throw new BusinessException("Cette livraison est déjà validée");
        }

        delivery.setValidated(true);

        Long currentUserId = ctx.currentUserId();
        auditService.log(currentUserId, "VALIDATE_DELIVERY", "Delivery", id, null, null);

        return mapper.toDeliveryResponse(deliveryRepository.save(delivery));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryResponse> getDeliveriesByStation(Long stationId) {
        ctx.requireAccessTo(stationId);
        return deliveryRepository.findByStationIdOrderByDeliveryDateDesc(stationId).stream()
                .map(mapper::toDeliveryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryResponse> getAllDeliveries() {
        List<Long> scopedIds = ctx.scopedStationIds();

        if (scopedIds == null) {
            // ADMIN: all deliveries
            return deliveryRepository.findAll().stream()
                    .map(mapper::toDeliveryResponse)
                    .collect(Collectors.toList());
        } else if (scopedIds.isEmpty()) {
            return Collections.emptyList();
        } else if (scopedIds.size() == 1) {
            // STATION_MANAGER: single station
            return getDeliveriesByStation(scopedIds.get(0));
        } else {
            // MANAGER: multiple stations in region
            return deliveryRepository.findByStationIdInOrderByDeliveryDateDesc(scopedIds).stream()
                    .map(mapper::toDeliveryResponse)
                    .collect(Collectors.toList());
        }
    }
}