package com.agil.energy.service;

import com.agil.energy.dto.request.StockAdjustmentRequest;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.StockMovement;
import com.agil.energy.entity.Tank;
import com.agil.energy.entity.User;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.StockMovementRepository;
import com.agil.energy.repository.TankRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.impl.StockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock private TankRepository tankRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityMapper mapper;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private StockServiceImpl stockService;

    private Station station;
    private Tank tank;
    private User user;

    @BeforeEach
    void setUp() {
        station = new Station();
        station.setId(1L);
        station.setName("Station Tunis");

        tank = Tank.builder()
                .id(1L)
                .station(station)
                .capacity(new BigDecimal("20000"))
                .currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000"))
                .build();

        user = new User();
        user.setId(1L);
        user.setName("Admin");
    }

    @Test
    @DisplayName("adjustStock — updates stock and records movement")
    void adjustStock_valid_updatesStockAndRecords() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setTankId(1L);
        request.setNewStock(new BigDecimal("8000"));
        request.setJustification("Correction inventaire");

        when(tankRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(tank));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tankRepository.save(any())).thenReturn(tank);
        when(mapper.toTankResponse(any())).thenReturn(new TankResponse());

        stockService.adjustStock(request, 1L);

        assertThat(tank.getCurrentStock()).isEqualByComparingTo("8000");
        verify(stockMovementRepository).save(any(StockMovement.class));
        verify(tankRepository).save(tank);
    }

    @Test
    @DisplayName("adjustStock — throws BusinessException when exceeding capacity")
    void adjustStock_exceedsCapacity_throwsException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setTankId(1L);
        request.setNewStock(new BigDecimal("99999"));

        when(tankRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(tank));

        assertThatThrownBy(() -> stockService.adjustStock(request, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("capacité");
    }

    @Test
    @DisplayName("adjustStock — throws ResourceNotFoundException for unknown tank")
    void adjustStock_unknownTank_throwsException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setTankId(99L);
        request.setNewStock(new BigDecimal("5000"));

        when(tankRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.adjustStock(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getStocksByStation — returns mapped list")
    void getStocksByStation_returnsList() {
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(mapper.toTankResponse(tank)).thenReturn(new TankResponse());

        List<TankResponse> result = stockService.getStocksByStation(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getCriticalTanks — delegates to repository")
    void getCriticalTanks_delegates() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);
        when(tankRepository.findTanksBelowThreshold()).thenReturn(List.of(tank));
        when(mapper.toTankResponse(tank)).thenReturn(new TankResponse());

        List<TankResponse> result = stockService.getCriticalTanks();

        assertThat(result).hasSize(1);
        verify(tankRepository).findTanksBelowThreshold();
    }
}