package com.agil.energy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnomalyStatsResponse {
    /** Total SALE_ANOMALY alerts created in the last 7 days. */
    private long totalLast7Days;

    /** Maximum absolute Z-score observed in the last 7 days (nullable if none). */
    private Double maxAbsZScore;

    /** Station with the most SALE_ANOMALY alerts in the last 7 days (nullable if none). */
    private TopStation topStation;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TopStation {
        private Long stationId;
        private String stationName;
        private long count;
    }
}