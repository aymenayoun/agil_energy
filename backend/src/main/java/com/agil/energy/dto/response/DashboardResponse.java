package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private int totalStations;
    private int activeStations;
    private long activeAlerts;
    private long criticalAlerts;
    private List<TankResponse> criticalTanks;
    private List<AlertResponse> recentAlerts;
    private List<StationSummary> stationSummaries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StationSummary {
        private Long stationId;
        private String stationName;
        private String region;
        private BigDecimal avgDailyConsumption;
        private BigDecimal daysBeforeRupture;
        private long activeAlertCount;
    }
}