package com.agil.energy.service;

import com.agil.energy.dto.response.DashboardResponse;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.Tank;
import com.agil.energy.entity.FuelType;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private StationRepository stationRepository;
    @Mock private TankRepository tankRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private EntityMapper mapper;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private DashboardServiceImpl dashboardService;

    private Station activeStation;

    @BeforeEach
    void setUp() {
        activeStation = new Station();
        activeStation.setId(1L);
        activeStation.setName("Station Tunis");
        activeStation.setRegion("Tunis");
        activeStation.setStatus(StationStatus.ACTIVE);
    }

    @Test
    @DisplayName("getDashboard — returns aggregated dashboard with station summaries")
    void getDashboard_noFilter_returnsDashboard() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);
        when(ctx.resolveStationId(null)).thenReturn(null);

        FuelType fuelType = new FuelType();
        fuelType.setId(1L);
        fuelType.setName("Gasoil");

        Tank tank = Tank.builder()
                .id(1L)
                .station(activeStation)
                .fuelType(fuelType)
                .capacity(new BigDecimal("20000"))
                .currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000"))
                .build();

        when(stationRepository.findAll()).thenReturn(List.of(activeStation));
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(activeStation));
        when(tankRepository.findTanksBelowThreshold()).thenReturn(Collections.emptyList());
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(3L);
        when(alertRepository.countBySeverityAndStatus(Severity.HIGH, AlertStatus.ACTIVE)).thenReturn(1L);
        when(alertRepository.findByStatus(eq(AlertStatus.ACTIVE), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(saleRepository.findAverageDailyConsumption(eq(1L), eq(1L), any(), any()))
                .thenReturn(new BigDecimal("200"));
        when(alertRepository.countByStationIdAndStatus(1L, AlertStatus.ACTIVE)).thenReturn(2L);

        DashboardResponse result = dashboardService.getDashboard(null);

        assertThat(result.getTotalStations()).isEqualTo(1);
        assertThat(result.getActiveStations()).isEqualTo(1);
        assertThat(result.getActiveAlerts()).isEqualTo(3L);
        assertThat(result.getCriticalAlerts()).isEqualTo(1L);
        assertThat(result.getStationSummaries()).hasSize(1);
        assertThat(result.getStationSummaries().get(0).getStationName()).isEqualTo("Station Tunis");
        assertThat(result.getStationSummaries().get(0).getDaysBeforeRupture()).isNotNull();
    }

    @Test
    @DisplayName("getDashboard — with stationId filter only summarizes that station")
    void getDashboard_withFilter_filtersSummaries() {
        // Simulate ADMIN requesting a specific station
        when(ctx.scopedStationIds()).thenReturn(null);
        when(ctx.resolveStationId(1L)).thenReturn(1L);
        // requireAccessTo is void — no-op on mock by default

        when(stationRepository.findById(1L)).thenReturn(java.util.Optional.of(activeStation));
        when(tankRepository.findCriticalTanksByStation(1L)).thenReturn(Collections.emptyList());
        when(alertRepository.countByStationIdAndStatus(1L, AlertStatus.ACTIVE)).thenReturn(0L);
        when(alertRepository.findByStationId(eq(1L), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(tankRepository.findByStationId(1L)).thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getDashboard(1L);

        assertThat(result.getStationSummaries()).hasSize(1);
    }

    @Test
    @DisplayName("getDashboard — station with no sales data still appears in summaries")
    void getDashboard_noSalesData_handlesGracefully() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);
        when(ctx.resolveStationId(null)).thenReturn(null);

        FuelType fuelType = new FuelType();
        fuelType.setId(1L);

        Tank tank = Tank.builder().id(1L).station(activeStation).fuelType(fuelType)
                .capacity(new BigDecimal("20000")).currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000")).build();

        when(stationRepository.findAll()).thenReturn(List.of(activeStation));
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(activeStation));
        when(tankRepository.findTanksBelowThreshold()).thenReturn(Collections.emptyList());
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(0L);
        when(alertRepository.countBySeverityAndStatus(Severity.HIGH, AlertStatus.ACTIVE)).thenReturn(0L);
        when(alertRepository.findByStatus(eq(AlertStatus.ACTIVE), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(saleRepository.findAverageDailyConsumption(eq(1L), eq(1L), any(), any())).thenReturn(null);
        when(alertRepository.countByStationIdAndStatus(1L, AlertStatus.ACTIVE)).thenReturn(0L);

        DashboardResponse result = dashboardService.getDashboard(null);

        assertThat(result.getStationSummaries()).hasSize(1);
        assertThat(result.getStationSummaries().get(0).getDaysBeforeRupture()).isNull();
    }
}