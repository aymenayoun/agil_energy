package com.agil.energy.service.impl;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.dto.response.AnomalyStatsResponse;
import com.agil.energy.entity.Alert;
import com.agil.energy.entity.User;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.AlertPublisher;
import com.agil.energy.service.AlertService;
import com.agil.energy.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertServiceImpl implements AlertService {

    private static final java.time.ZoneId TUNIS = java.time.ZoneId.of("Africa/Tunis");

    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final EntityMapper mapper;
    private final AuditService auditService;
    private final AlertPublisher alertPublisher;
    private final CurrentUserContext ctx;

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getActiveAlerts(AlertType alertType, Pageable pageable) {
        // scopedStationIds() returns:
        //   null          → ADMIN (no filter)
        //   [id1,id2,...] → MANAGER (region stations)
        //   [id]          → STATION_MANAGER (single station)
        //   []            → no access
        List<Long> scopedIds = ctx.scopedStationIds();

        Page<Alert> page;

        if (scopedIds == null) {
            // ADMIN: global
            page = (alertType != null)
                    ? alertRepository.findByStatusAndAlertType(AlertStatus.ACTIVE, alertType, pageable)
                    : alertRepository.findByStatus(AlertStatus.ACTIVE, pageable);
        } else if (scopedIds.size() == 1) {
            // STATION_MANAGER: single station
            Long stationId = scopedIds.get(0);
            page = (alertType != null)
                    ? alertRepository.findByStationIdAndAlertType(stationId, alertType, pageable)
                    : alertRepository.findByStationId(stationId, pageable);
        } else if (!scopedIds.isEmpty()) {
            // MANAGER: multiple stations in region
            page = (alertType != null)
                    ? alertRepository.findByStationIdInAndStatusAndAlertType(
                    scopedIds, AlertStatus.ACTIVE, alertType, pageable)
                    : alertRepository.findByStationIdInAndStatus(scopedIds, AlertStatus.ACTIVE, pageable);
        } else {
            return Page.empty(pageable);
        }

        return page.map(mapper::toAlertResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlertsByStation(Long stationId, AlertType alertType, Pageable pageable) {
        ctx.requireAccessTo(stationId);
        Page<Alert> page = (alertType != null)
                ? alertRepository.findByStationIdAndAlertType(stationId, alertType, pageable)
                : alertRepository.findByStationId(stationId, pageable);
        return page.map(mapper::toAlertResponse);
    }

    @Override
    public AlertResponse resolveAlert(Long alertId, Long userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte", alertId));
        ctx.requireAccessTo(alert.getStation().getId());

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new BusinessException("Cette alerte est déjà résolue");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(user);
        alert.setResolvedAt(LocalDateTime.now());

        auditService.log(userId, "RESOLVE_ALERT", "Alert", alertId, null, null);

        Alert saved = alertRepository.save(alert);
        alertPublisher.publish(saved);
        return mapper.toAlertResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveAlerts() {
        List<Long> scopedIds = ctx.scopedStationIds();

        if (scopedIds == null) {
            return alertRepository.countByStatus(AlertStatus.ACTIVE);
        } else if (scopedIds.isEmpty()) {
            return 0;
        } else if (scopedIds.size() == 1) {
            return alertRepository.countByStationIdAndStatus(scopedIds.get(0), AlertStatus.ACTIVE);
        } else {
            return alertRepository.countByStationIdInAndStatus(scopedIds, AlertStatus.ACTIVE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AnomalyStatsResponse getAnomalyStats() {
        LocalDateTime since = LocalDateTime.now(TUNIS).minusDays(7);

        long total = alertRepository.countByAlertTypeAndCreatedAtAfter(AlertType.SALE_ANOMALY, since);
        Double maxZ = alertRepository.findMaxAbsZScoreSince(AlertType.SALE_ANOMALY, since);

        List<Object[]> topRows = alertRepository.findTopStationsByAlertTypeSince(
                AlertType.SALE_ANOMALY, since, PageRequest.of(0, 1));

        AnomalyStatsResponse.TopStation topStation = null;
        if (!topRows.isEmpty()) {
            Object[] row = topRows.get(0);
            topStation = AnomalyStatsResponse.TopStation.builder()
                    .stationId((Long) row[0])
                    .stationName((String) row[1])
                    .count(((Number) row[2]).longValue())
                    .build();
        }

        return AnomalyStatsResponse.builder()
                .totalLast7Days(total)
                .maxAbsZScore(maxZ)
                .topStation(topStation)
                .build();
    }
}