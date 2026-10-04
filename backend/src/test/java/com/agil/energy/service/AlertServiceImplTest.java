package com.agil.energy.service;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.entity.Alert;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.User;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.AlertPublisher;
import com.agil.energy.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock private AlertRepository alertRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityMapper mapper;
    @Mock private AuditService auditService;
    @Mock private AlertPublisher alertPublisher;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private AlertServiceImpl alertService;

    private Station station;
    private Alert activeAlert;
    private AlertResponse alertResponse;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        station = new Station();
        station.setId(1L);
        station.setName("Station Tunis");

        activeAlert = Alert.builder()
                .id(1L)
                .station(station)
                .alertType(AlertType.STOCK_RUPTURE)
                .severity(Severity.HIGH)
                .message("Test alert")
                .status(AlertStatus.ACTIVE)
                .build();

        alertResponse = new AlertResponse();
        alertResponse.setId(1L);
        alertResponse.setStatus("ACTIVE");
    }

    // ========================================================================
    // getActiveAlerts
    // ========================================================================

    @Test
    @DisplayName("getActiveAlerts — null alertType returns paginated active alerts (no type filter)")
    void getActiveAlerts_noFilter_returnsPaginatedResults() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Alert> page = new PageImpl<>(List.of(activeAlert));

        when(alertRepository.findByStatus(AlertStatus.ACTIVE, pageable)).thenReturn(page);
        when(mapper.toAlertResponse(activeAlert)).thenReturn(alertResponse);

        Page<AlertResponse> result = alertService.getActiveAlerts(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        verify(alertRepository).findByStatus(AlertStatus.ACTIVE, pageable);
        verify(alertRepository, never()).findByStatusAndAlertType(any(), any(), any());
    }

    @Test
    @DisplayName("getActiveAlerts — with alertType filter, uses filtered repository query")
    void getActiveAlerts_withFilter_usesFilteredQuery() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Alert> page = new PageImpl<>(List.of(activeAlert));

        when(alertRepository.findByStatusAndAlertType(AlertStatus.ACTIVE, AlertType.SALE_ANOMALY, pageable))
                .thenReturn(page);
        when(mapper.toAlertResponse(activeAlert)).thenReturn(alertResponse);

        Page<AlertResponse> result = alertService.getActiveAlerts(AlertType.SALE_ANOMALY, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(alertRepository).findByStatusAndAlertType(AlertStatus.ACTIVE, AlertType.SALE_ANOMALY, pageable);
        verify(alertRepository, never()).findByStatus(any(), any());
    }

    // ========================================================================
    // getAlertsByStation
    // ========================================================================

    @Test
    @DisplayName("getAlertsByStation — null alertType delegates to unfiltered query")
    void getAlertsByStation_noFilter_delegates() {
        // ctx.requireAccessTo is void — no-op on mock by default
        Pageable pageable = PageRequest.of(0, 20);
        when(alertRepository.findByStationId(42L, pageable))
                .thenReturn(new PageImpl<>(List.of(activeAlert)));
        when(mapper.toAlertResponse(any())).thenReturn(alertResponse);

        alertService.getAlertsByStation(42L, null, pageable);

        verify(alertRepository).findByStationId(42L, pageable);
        verify(alertRepository, never()).findByStationIdAndAlertType(any(), any(), any());
    }

    @Test
    @DisplayName("getAlertsByStation — with filter delegates to filtered query")
    void getAlertsByStation_withFilter_delegates() {
        Pageable pageable = PageRequest.of(0, 20);
        when(alertRepository.findByStationIdAndAlertType(42L, AlertType.SALE_ANOMALY, pageable))
                .thenReturn(new PageImpl<>(List.of(activeAlert)));
        when(mapper.toAlertResponse(any())).thenReturn(alertResponse);

        alertService.getAlertsByStation(42L, AlertType.SALE_ANOMALY, pageable);

        verify(alertRepository).findByStationIdAndAlertType(42L, AlertType.SALE_ANOMALY, pageable);
        verify(alertRepository, never()).findByStationId(any(), any());
    }

    // ========================================================================
    // resolveAlert
    // ========================================================================

    @Test
    @DisplayName("resolveAlert — successfully resolves an active alert and writes audit log")
    void resolveAlert_success() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(activeAlert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(alertRepository.save(any(Alert.class))).thenReturn(activeAlert);
        when(mapper.toAlertResponse(any())).thenReturn(alertResponse);

        alertService.resolveAlert(1L, 1L);

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(activeAlert.getResolvedBy()).isEqualTo(user);
        assertThat(activeAlert.getResolvedAt()).isNotNull();
        verify(alertRepository).save(activeAlert);
        verify(auditService).log(1L, "RESOLVE_ALERT", "Alert", 1L, null, null);
        verify(alertPublisher).publish(activeAlert);
    }

    @Test
    @DisplayName("resolveAlert — throws BusinessException if already resolved")
    void resolveAlert_alreadyResolved_throwsException() {
        activeAlert.setStatus(AlertStatus.RESOLVED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(activeAlert));

        assertThatThrownBy(() -> alertService.resolveAlert(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà résolue");
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("resolveAlert — throws ResourceNotFoundException for unknown alert")
    void resolveAlert_unknownAlert_throwsException() {
        when(alertRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.resolveAlert(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========================================================================
    // countActiveAlerts
    // ========================================================================

    @Test
    @DisplayName("countActiveAlerts — delegates to repository")
    void countActiveAlerts_delegatesToRepository() {
        // Simulate ADMIN scope
        when(ctx.scopedStationIds()).thenReturn(null);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(5L);

        long count = alertService.countActiveAlerts();

        assertThat(count).isEqualTo(5L);
        verify(alertRepository).countByStatus(AlertStatus.ACTIVE);
    }
}