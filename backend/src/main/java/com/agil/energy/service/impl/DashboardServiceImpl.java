package com.agil.energy.service.impl;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.dto.response.DashboardResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.Tank;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final StationRepository stationRepository;
    private final TankRepository tankRepository;
    private final SaleRepository saleRepository;
    private final AlertRepository alertRepository;
    private final EntityMapper mapper;
    private final CurrentUserContext ctx;

    @Override
    public DashboardResponse getDashboard(Long stationId) {
        // ---- Determine the effective scope ----
        // scopedStationIds() returns:
        //   null          → ADMIN (no filter)
        //   [id1,id2,...] → MANAGER (all stations in their region)
        //   [id]          → STATION_MANAGER (single station) — though SM is blocked at route level
        //   []            → no access
        List<Long> scopedIds = ctx.scopedStationIds();

        // If a specific stationId was requested, validate access
        Long effectiveStationId = ctx.resolveStationId(stationId);
        if (effectiveStationId != null) {
            ctx.requireAccessTo(effectiveStationId);
        }

        // When viewing a specific station (drill-down), use single-station logic
        if (effectiveStationId != null) {
            return buildSingleStationDashboard(effectiveStationId);
        }

        // When viewing "all" — apply the scope
        if (scopedIds != null && scopedIds.isEmpty()) {
            return buildEmptyDashboard();
        }

        return buildScopedDashboard(scopedIds);
    }

    // ==================== Single station drill-down ====================

    private DashboardResponse buildSingleStationDashboard(Long stationId) {
        List<Station> allStations = List.of(stationRepository.findById(stationId).orElseThrow());
        List<Station> activeStations = allStations.stream()
                .filter(s -> s.getStatus() == StationStatus.ACTIVE)
                .collect(Collectors.toList());

        List<TankResponse> criticalTanks = tankRepository.findCriticalTanksByStation(stationId).stream()
                .map(mapper::toTankResponse).collect(Collectors.toList());

        long activeAlertCount = alertRepository.countByStationIdAndStatus(stationId, AlertStatus.ACTIVE);
        long criticalAlertCount = alertRepository
                .findByStationId(stationId, PageRequest.of(0, 1000)).stream()
                .filter(a -> a.getStatus() == AlertStatus.ACTIVE && a.getSeverity() == Severity.HIGH)
                .count();

        List<AlertResponse> recentAlerts = alertRepository
                .findByStationId(stationId,
                        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(mapper::toAlertResponse).collect(Collectors.toList());

        List<DashboardResponse.StationSummary> summaries = buildSummaries(activeStations);

        return DashboardResponse.builder()
                .totalStations(1)
                .activeStations(activeStations.size())
                .activeAlerts(activeAlertCount)
                .criticalAlerts(criticalAlertCount)
                .criticalTanks(criticalTanks)
                .recentAlerts(recentAlerts)
                .stationSummaries(summaries)
                .build();
    }

    // ==================== Multi-station (scoped or global) ====================

    private DashboardResponse buildScopedDashboard(List<Long> scopedIds) {
        // scopedIds == null means ADMIN (global); otherwise MANAGER (region)
        List<Station> allStations;
        List<Station> activeStations;

        if (scopedIds == null) {
            allStations = stationRepository.findAll();
            activeStations = stationRepository.findByStatus(StationStatus.ACTIVE);
        } else {
            allStations = stationRepository.findByIdIn(scopedIds);
            activeStations = stationRepository.findByStatusAndIdIn(StationStatus.ACTIVE, scopedIds);
        }

        // Critical tanks
        List<TankResponse> criticalTanks;
        if (scopedIds == null) {
            criticalTanks = tankRepository.findTanksBelowThreshold().stream()
                    .map(mapper::toTankResponse).collect(Collectors.toList());
        } else {
            criticalTanks = tankRepository.findCriticalTanksByStationIdIn(scopedIds).stream()
                    .map(mapper::toTankResponse).collect(Collectors.toList());
        }

        // Alert counts
        long activeAlertCount;
        long criticalAlertCount;
        if (scopedIds == null) {
            activeAlertCount = alertRepository.countByStatus(AlertStatus.ACTIVE);
            criticalAlertCount = alertRepository.countBySeverityAndStatus(Severity.HIGH, AlertStatus.ACTIVE);
        } else {
            activeAlertCount = alertRepository.countByStationIdInAndStatus(scopedIds, AlertStatus.ACTIVE);
            criticalAlertCount = alertRepository.countByStationIdInAndSeverityAndStatus(
                    scopedIds, Severity.HIGH, AlertStatus.ACTIVE);
        }

        // Recent alerts
        List<AlertResponse> recentAlerts;
        if (scopedIds == null) {
            recentAlerts = alertRepository
                    .findByStatus(AlertStatus.ACTIVE,
                            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream().map(mapper::toAlertResponse).collect(Collectors.toList());
        } else {
            recentAlerts = alertRepository
                    .findByStationIdInAndStatus(scopedIds, AlertStatus.ACTIVE,
                            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream().map(mapper::toAlertResponse).collect(Collectors.toList());
        }

        // Station summaries
        List<DashboardResponse.StationSummary> summaries = buildSummaries(activeStations);

        return DashboardResponse.builder()
                .totalStations(allStations.size())
                .activeStations(activeStations.size())
                .activeAlerts(activeAlertCount)
                .criticalAlerts(criticalAlertCount)
                .criticalTanks(criticalTanks)
                .recentAlerts(recentAlerts)
                .stationSummaries(summaries)
                .build();
    }

    // ==================== Helpers ====================

    private DashboardResponse buildEmptyDashboard() {
        return DashboardResponse.builder()
                .totalStations(0)
                .activeStations(0)
                .activeAlerts(0)
                .criticalAlerts(0)
                .criticalTanks(Collections.emptyList())
                .recentAlerts(Collections.emptyList())
                .stationSummaries(Collections.emptyList())
                .build();
    }

    private List<DashboardResponse.StationSummary> buildSummaries(List<Station> stations) {
        List<DashboardResponse.StationSummary> summaries = new ArrayList<>();

        for (Station station : stations) {
            List<Tank> tanks = tankRepository.findByStationId(station.getId());

            BigDecimal totalAvg = BigDecimal.ZERO;
            BigDecimal minDaysBeforeRupture = null;

            for (Tank tank : tanks) {
                BigDecimal avg = saleRepository.findAverageDailyConsumption(
                        station.getId(),
                        tank.getFuelType().getId(),
                        LocalDate.now().minusDays(30),
                        LocalDate.now());

                if (avg != null && avg.compareTo(BigDecimal.ZERO) > 0) {
                    totalAvg = totalAvg.add(avg);
                    BigDecimal daysLeft = tank.getCurrentStock()
                            .divide(avg, 1, RoundingMode.HALF_UP);
                    if (minDaysBeforeRupture == null || daysLeft.compareTo(minDaysBeforeRupture) < 0) {
                        minDaysBeforeRupture = daysLeft;
                    }
                }
            }

            long stationAlertCount = alertRepository.countByStationIdAndStatus(
                    station.getId(), AlertStatus.ACTIVE);

            summaries.add(DashboardResponse.StationSummary.builder()
                    .stationId(station.getId())
                    .stationName(station.getName())
                    .region(station.getRegion())
                    .avgDailyConsumption(totalAvg)
                    .daysBeforeRupture(minDaysBeforeRupture)
                    .activeAlertCount(stationAlertCount)
                    .build());
        }

        return summaries;
    }
}