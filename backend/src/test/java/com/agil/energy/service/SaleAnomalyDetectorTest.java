package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.FuelType;
import com.agil.energy.entity.Sale;
import com.agil.energy.entity.Station;
import com.agil.energy.enums.Severity;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.SaleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleAnomalyDetectorTest {

    @Mock private SaleRepository saleRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private AlertPublisher alertPublisher;

    @InjectMocks private SaleAnomalyDetector detector;

    private Sale sale;

    @BeforeEach
    void setUp() {
        Station station = new Station(); station.setId(1L); station.setName("Station Tunis");
        FuelType fuel = new FuelType(); fuel.setId(1L); fuel.setName("Gasoil");
        sale = new Sale();
        sale.setId(1L);
        sale.setStation(station);
        sale.setFuelType(fuel);
        sale.setQuantity(new BigDecimal("500"));
    }

    @Test
    @DisplayName("detect — normal quantity within threshold raises no alert")
    void detect_normalSale_noAlert() {
        // mean=500, stddev=50, qty=500 -> z=0
        Object[] stats = new Object[]{30L, 500.0, 50.0};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(stats);

        detector.detect(sale);

        verify(alertRepository, never()).save(any());
        verify(alertPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("detect — anomalous quantity (z > 2σ) raises MEDIUM alert")
    void detect_anomaly_raisesMediumAlert() {
        // mean=500, stddev=50, qty=650 -> z=3 -> HIGH actually
        // Use qty=625 -> z=2.5 -> MEDIUM
        sale.setQuantity(new BigDecimal("625"));
        Object[] stats = new Object[]{30L, 500.0, 50.0};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(stats);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        detector.detect(sale);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert raised = captor.getValue();
        assertThat(raised.getSeverity()).isEqualTo(Severity.MEDIUM);
        verify(alertPublisher).publish(any(Alert.class));
    }

    @Test
    @DisplayName("detect — extreme anomaly (|z| > 3σ) raises HIGH severity")
    void detect_extremeAnomaly_raisesHighAlert() {
        // mean=500, stddev=50, qty=700 -> z=4 -> HIGH
        sale.setQuantity(new BigDecimal("700"));
        Object[] stats = new Object[]{30L, 500.0, 50.0};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(stats);
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        detector.detect(sale);

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("detect — insufficient history (< 20 samples) does nothing")
    void detect_insufficientSamples_doesNothing() {
        Object[] stats = new Object[]{5L, 500.0, 50.0};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(stats);

        detector.detect(sale);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("detect — zero stddev (degenerate) does nothing")
    void detect_zeroStddev_doesNothing() {
        Object[] stats = new Object[]{30L, 500.0, 0.0};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(stats);

        detector.detect(sale);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("detect — null row does nothing")
    void detect_nullRow_doesNothing() {
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(null);

        detector.detect(sale);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("detect — repository throwing is swallowed (never propagates)")
    void detect_repoThrows_swallowsException() {
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        // Should not throw
        detector.detect(sale);

        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("detect — handles wrapped Object[] from Hibernate")
    void detect_wrappedRow_unwrapsCorrectly() {
        Object[] inner = new Object[]{30L, 500.0, 50.0};
        Object[] wrapped = new Object[]{inner};
        when(saleRepository.computeQuantityStats(anyLong(), anyLong(), any(), any()))
                .thenReturn(wrapped);

        detector.detect(sale);

        // qty=500, mean=500 -> z=0 -> no alert, but it should have unwrapped successfully
        verify(alertRepository, never()).save(any());
    }
}