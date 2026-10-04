package com.agil.energy.mapper;

import com.agil.energy.dto.response.*;
import com.agil.energy.entity.*;
import com.agil.energy.enums.UserStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Component
public class EntityMapper {

    // ===== USER =====
    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roleName(user.getRole().getName())
                .status(user.getStatus().name())
                .stationId(user.getStation() != null ? user.getStation().getId() : null)
                .stationName(user.getStation() != null ? user.getStation().getName() : null)
                .region(user.getRegion())
                .createdAt(user.getCreatedAt())
                .build();
    }

    // ===== STATION =====
    public StationResponse toStationResponse(Station station) {
        // Manager is now derived from assignedUsers: the active STATION_MANAGER, if any.
        User manager = station.getAssignedUsers() == null ? null :
                station.getAssignedUsers().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> u.getRole() != null && "STATION_MANAGER".equals(u.getRole().getName()))
                .findFirst()
                .orElse(null);

        return StationResponse.builder()
                .id(station.getId())
                .name(station.getName())
                .region(station.getRegion())
                .address(station.getAddress())
                .latitude(station.getLatitude())
                .longitude(station.getLongitude())
                .status(station.getStatus().name())
                .managerId(manager != null ? manager.getId() : null)
                .managerName(manager != null ? manager.getName() : null)
                .tanks(station.getTanks() != null
                        ? station.getTanks().stream().map(this::toTankResponse).collect(Collectors.toList())
                        : null)
                .createdAt(station.getCreatedAt())
                .build();
    }

    // ===== TANK =====
    public TankResponse toTankResponse(Tank tank) {
        BigDecimal stockPercentage = BigDecimal.ZERO;
        if (tank.getCapacity().compareTo(BigDecimal.ZERO) > 0) {
            stockPercentage = tank.getCurrentStock()
                    .multiply(new BigDecimal("100"))
                    .divide(tank.getCapacity(), 2, RoundingMode.HALF_UP);
        }

        return TankResponse.builder()
                .id(tank.getId())
                .stationId(tank.getStation().getId())
                .stationName(tank.getStation().getName())
                .fuelTypeId(tank.getFuelType().getId())
                .fuelTypeName(tank.getFuelType().getName())
                .capacity(tank.getCapacity())
                .currentStock(tank.getCurrentStock())
                .criticalThreshold(tank.getCriticalThreshold())
                .stockPercentage(stockPercentage)
                .critical(tank.getCurrentStock().compareTo(tank.getCriticalThreshold()) <= 0)
                .build();
    }

    // ===== SALE =====
    public SaleResponse toSaleResponse(Sale sale) {
        return SaleResponse.builder()
                .id(sale.getId())
                .stationId(sale.getStation().getId())
                .stationName(sale.getStation().getName())
                .fuelTypeId(sale.getFuelType().getId())
                .fuelTypeName(sale.getFuelType().getName())
                .saleDate(sale.getSaleDate())
                .quantity(sale.getQuantity())
                .validated(sale.getValidated())
                .createdAt(sale.getCreatedAt())
                .build();
    }

    // ===== DELIVERY =====
    public DeliveryResponse toDeliveryResponse(Delivery delivery) {
        return DeliveryResponse.builder()
                .id(delivery.getId())
                .stationId(delivery.getStation().getId())
                .stationName(delivery.getStation().getName())
                .fuelTypeId(delivery.getFuelType().getId())
                .fuelTypeName(delivery.getFuelType().getName())
                .deliveryDate(delivery.getDeliveryDate())
                .quantity(delivery.getQuantity())
                .validated(delivery.getValidated())
                .createdByName(delivery.getCreatedBy() != null ? delivery.getCreatedBy().getName() : null)
                .createdAt(delivery.getCreatedAt())
                .build();
    }

    // ===== ALERT =====
    public AlertResponse toAlertResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .stationId(alert.getStation().getId())
                .stationName(alert.getStation().getName())
                .alertType(alert.getAlertType().name())
                .severity(alert.getSeverity().name())
                .message(alert.getMessage())
                .status(alert.getStatus().name())
                .resolvedByName(alert.getResolvedBy() != null ? alert.getResolvedBy().getName() : null)
                .resolvedAt(alert.getResolvedAt())
                .createdAt(alert.getCreatedAt())
                .build();
    }

    // ===== PREDICTION =====
    public PredictionResponse toPredictionResponse(Prediction prediction) {
        return PredictionResponse.builder()
                .id(prediction.getId())
                .stationId(prediction.getStation().getId())
                .stationName(prediction.getStation().getName())
                .fuelTypeId(prediction.getFuelType().getId())
                .fuelTypeName(prediction.getFuelType().getName())
                .predictionDate(prediction.getPredictionDate())
                .predictedQuantity(prediction.getPredictedQuantity())
                .modelName(prediction.getModelName())
                .confidenceScore(prediction.getConfidenceScore())
                .generatedAt(prediction.getGeneratedAt())
                .build();
    }

    // ===== STOCK MOVEMENT =====
    public StockMovementResponse toStockMovementResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .tankId(movement.getTank().getId())
                .fuelTypeName(movement.getTank().getFuelType().getName())
                .stationName(movement.getTank().getStation().getName())
                .movementType(movement.getMovementType().name())
                .quantity(movement.getQuantity())
                .stockBefore(movement.getStockBefore())
                .stockAfter(movement.getStockAfter())
                .justification(movement.getJustification())
                .createdByName(movement.getCreatedBy() != null ? movement.getCreatedBy().getName() : null)
                .createdAt(movement.getCreatedAt())
                .build();
    }
}