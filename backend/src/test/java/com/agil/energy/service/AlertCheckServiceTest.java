package com.agil.energy.service;

import com.agil.energy.entity.*;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.SaleRepository;
import com.agil.energy.repository.StationRepository;
import com.agil.energy.repository.TankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertCheckServiceTest {

    @Mock private TankRepository tankRepository;
    @Mock private StationRepository stationRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private AlertPublisher alertPublisher;

    @InjectMocks private AlertCheckService alertCheckService;

    private Station station;
    private FuelType fuel;
    private Tank tank;

    @BeforeEach
    void setUp() {
        station = new Station(); station.setId(1L); station.setName("Station Tunis");
        station.setStatus(StationStatus.ACTIVE);
        fuel = new FuelType(); fuel.setId(1L); fuel.setName("Gasoil");

        tank = Tank.builder()
                .id(1L).station(station).fuelType(fuel)
                .capacity(new BigDecimal("20000"))
                .currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000"))
                .build();
    }

    // ── checkStockAlerts ──────────────────────────────────────

    @Test
    @DisplayName("checkStockAlerts — tank not found is no-op")
    void checkStockAlerts_tankMissing_noOp() {
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.empty());

        alertCheckService.checkStockAlerts(1L, 1L);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStockAlerts — stock above threshold raises no alert")
    void checkStockAlerts_aboveThreshold_noAlert() {
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(tank));

        alertCheckService.checkStockAlerts(1L, 1L);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStockAlerts — stock at threshold raises STOCK_RUPTURE MEDIUM (>10% capacity)")
    void checkStockAlerts_belowThreshold_raisesMedium() {
        // 2000/20000 = 10% — boundary is <= 10% means HIGH; let's use 15%
        tank.setCurrentStock(new BigDecimal("3000"));
        tank.setCriticalThreshold(new BigDecimal("3500"));
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(tank));
        when(alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                eq(1L), eq(AlertType.STOCK_RUPTURE), eq(AlertStatus.ACTIVE), anyString()))
                .thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertCheckService.checkStockAlerts(1L, 1L);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.getAlertType()).isEqualTo(AlertType.STOCK_RUPTURE);
        assertThat(alert.getSeverity()).isEqualTo(Severity.MEDIUM);
        verify(alertPublisher).publish(any(Alert.class));
    }

    @Test
    @DisplayName("checkStockAlerts — stock <= 10% capacity raises HIGH severity")
    void checkStockAlerts_veryLowStock_raisesHigh() {
        tank.setCurrentStock(new BigDecimal("1500"));   // 7.5% of 20000
        tank.setCriticalThreshold(new BigDecimal("2000"));
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(tank));
        when(alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                anyLong(), any(), any(), anyString())).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertCheckService.checkStockAlerts(1L, 1L);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("checkStockAlerts — duplicate active alert is suppressed")
    void checkStockAlerts_duplicate_suppressed() {
        tank.setCurrentStock(new BigDecimal("1000"));
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(tank));
        when(alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                eq(1L), eq(AlertType.STOCK_RUPTURE), eq(AlertStatus.ACTIVE), anyString()))
                .thenReturn(true);

        alertCheckService.checkStockAlerts(1L, 1L);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStockAlerts — negative stock raises STOCK_INCOHERENT alert")
    void checkStockAlerts_negativeStock_raisesIncoherent() {
        tank.setCurrentStock(new BigDecimal("-100"));
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(tank));
        when(alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                anyLong(), any(), any(), anyString())).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertCheckService.checkStockAlerts(1L, 1L);

        // Negative stock triggers BOTH STOCK_RUPTURE (since <= threshold) AND STOCK_INCOHERENT
        verify(alertRepository, atLeastOnce()).save(argThat(a ->
                a.getAlertType() == AlertType.STOCK_INCOHERENT
                        && a.getSeverity() == Severity.HIGH));
    }

    // ── checkMissingEntries ───────────────────────────────────

    @Test
    @DisplayName("checkMissingEntries — station with no sale yesterday raises MISSING_ENTRY")
    void checkMissingEntries_noSale_raisesAlert() {
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(station));
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(false);
        when(alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                anyLong(), eq(AlertType.MISSING_ENTRY), any(), anyString())).thenReturn(false);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = alertCheckService.checkMissingEntries();

        assertThat(count).isEqualTo(1);
        verify(alertRepository).save(argThat(a ->
                a.getAlertType() == AlertType.MISSING_ENTRY
                        && a.getSeverity() == Severity.LOW));
    }

    @Test
    @DisplayName("checkMissingEntries — sale exists yesterday raises no alert")
    void checkMissingEntries_saleExists_noAlert() {
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(station));
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(eq(1L), eq(1L), any(LocalDate.class)))
                .thenReturn(true);

        int count = alertCheckService.checkMissingEntries();

        assertThat(count).isZero();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkMissingEntries — no active stations returns zero")
    void checkMissingEntries_noStations_returnsZero() {
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(Collections.emptyList());

        int count = alertCheckService.checkMissingEntries();

        assertThat(count).isZero();
    }

    // ── dailyAlertCheck ───────────────────────────────────────

    @Test
    @DisplayName("dailyAlertCheck — runs through all tanks and stations")
    void dailyAlertCheck_runsFullScan() {
        when(tankRepository.findAll()).thenReturn(List.of(tank));
        when(stationRepository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(station));
        when(tankRepository.findByStationId(1L)).thenReturn(List.of(tank));
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(any(), any(), any())).thenReturn(true);

        alertCheckService.dailyAlertCheck();

        verify(tankRepository).findAll();
        verify(stationRepository).findByStatus(StationStatus.ACTIVE);
    }
}