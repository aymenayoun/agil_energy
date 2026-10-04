package com.agil.energy.service.impl;

import com.agil.energy.dto.request.StockAdjustmentRequest;
import com.agil.energy.dto.response.StockMovementResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.StockMovement;
import com.agil.energy.entity.Tank;
import com.agil.energy.entity.User;
import com.agil.energy.enums.MovementType;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.StockMovementRepository;
import com.agil.energy.repository.TankRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StockServiceImpl implements StockService {

    private final TankRepository tankRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final EntityMapper mapper;
    private final CurrentUserContext ctx;

    @Override
    @Transactional(readOnly = true)
    public List<TankResponse> getStocksByStation(Long stationId) {
        ctx.requireAccessTo(stationId);
        return tankRepository.findByStationId(stationId).stream()
                .map(mapper::toTankResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TankResponse adjustStock(StockAdjustmentRequest request, Long userId) {
        Tank tank = tankRepository.findByIdForUpdate(request.getTankId())
                .orElseThrow(() -> new ResourceNotFoundException("Réservoir", request.getTankId()));
        ctx.requireAccessTo(tank.getStation().getId());

        if (request.getNewStock().compareTo(tank.getCapacity()) > 0) {
            throw new BusinessException("Le stock ne peut pas dépasser la capacité du réservoir: "
                    + tank.getCapacity() + "L");
        }

        BigDecimal stockBefore = tank.getCurrentStock();
        BigDecimal quantity = request.getNewStock().subtract(stockBefore).abs();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        StockMovement movement = StockMovement.builder()
                .tank(tank)
                .movementType(MovementType.ADJUSTMENT)
                .quantity(quantity)
                .stockBefore(stockBefore)
                .stockAfter(request.getNewStock())
                .justification(request.getJustification())
                .createdBy(user)
                .build();
        stockMovementRepository.save(movement);

        tank.setCurrentStock(request.getNewStock());
        return mapper.toTankResponse(tankRepository.save(tank));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TankResponse> getCriticalTanks() {
        List<Long> scopedIds = ctx.scopedStationIds();

        if (scopedIds == null) {
            // ADMIN: all critical tanks
            return tankRepository.findTanksBelowThreshold().stream()
                    .map(mapper::toTankResponse)
                    .collect(Collectors.toList());
        } else if (scopedIds.isEmpty()) {
            return Collections.emptyList();
        } else if (scopedIds.size() == 1) {
            // STATION_MANAGER: single station
            return tankRepository.findCriticalTanksByStation(scopedIds.get(0)).stream()
                    .map(mapper::toTankResponse)
                    .collect(Collectors.toList());
        } else {
            // MANAGER: multiple stations in region
            return tankRepository.findCriticalTanksByStationIdIn(scopedIds).stream()
                    .map(mapper::toTankResponse)
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> getMovementsByTank(Long tankId) {
        Tank tank = tankRepository.findById(tankId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservoir", tankId));
        ctx.requireAccessTo(tank.getStation().getId());
        return stockMovementRepository.findByTankIdOrderByCreatedAtDesc(tankId).stream()
                .map(mapper::toStockMovementResponse)
                .collect(Collectors.toList());
    }
}