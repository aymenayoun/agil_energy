package com.agil.energy.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MonthlyReportData {

    // ----- Header / context -----
    private Long stationId;
    private String stationName;
    private String stationRegion;
    private String stationAddress;
    private String stationStatus;
    private int year;
    private int month;
    private String monthName;          // "Avril"
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate generatedAt;
    private String generatedBy;

    // ----- Top-level KPIs -----
    private BigDecimal totalSales;
    private BigDecimal totalDeliveries;
    private long activeAlertsAtEndOfPeriod;
    private long alertsCreatedDuringPeriod;
    private int tankCount;

    // ----- Per fuel type breakdown -----
    private List<FuelStat> fuelStats;

    // ----- Daily series (used by charts) -----
    private List<DailySeries> dailySalesByFuel;
    private List<DailySeries> dailyDeliveriesByFuel;

    // ----- Tanks snapshot at generation time -----
    private List<TankSnapshot> tanks;

    // ----- Detailed tables -----
    private List<AlertSummary> alerts;
    private List<SaleSummary> sales;
    private List<DeliverySummary> deliveries;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FuelStat {
        private Long fuelTypeId;
        private String fuelTypeName;
        private BigDecimal totalSales;
        private BigDecimal totalDeliveries;
        private BigDecimal avgDailySales;
        private BigDecimal currentStock;
        private BigDecimal capacity;
        private BigDecimal stockPercentage;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailySeries {
        private Long fuelTypeId;
        private String fuelTypeName;
        private List<DailyPoint> points;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyPoint {
        private LocalDate date;
        private BigDecimal value;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TankSnapshot {
        private Long tankId;
        private String fuelTypeName;
        private BigDecimal capacity;
        private BigDecimal currentStock;
        private BigDecimal criticalThreshold;
        private BigDecimal stockPercentage;
        private boolean critical;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AlertSummary {
        private LocalDate createdAt;
        private String alertType;
        private String severity;
        private String message;
        private String status;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaleSummary {
        private LocalDate saleDate;
        private String fuelTypeName;
        private BigDecimal quantity;
        private boolean validated;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeliverySummary {
        private LocalDate deliveryDate;
        private String fuelTypeName;
        private BigDecimal quantity;
        private boolean validated;
    }
}