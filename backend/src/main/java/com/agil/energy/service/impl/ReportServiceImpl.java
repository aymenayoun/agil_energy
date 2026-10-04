package com.agil.energy.service.impl;

import com.agil.energy.dto.response.MonthlyReportData;
import com.agil.energy.entity.*;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.repository.*;
import com.agil.energy.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final StationRepository stationRepository;
    private final TankRepository tankRepository;
    private final SaleRepository saleRepository;
    private final DeliveryRepository deliveryRepository;
    private final AlertRepository alertRepository;

    private final PdfReportGenerator pdfGenerator;
    private final ExcelReportGenerator excelGenerator;

    @Override
    public MonthlyReportData buildMonthlyReportData(Long stationId, int year, int month, String generatedBy) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station", stationId));

        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Sale> sales = saleRepository
                .findByStationIdAndSaleDateBetweenOrderBySaleDateAsc(stationId, start, end);
        List<Delivery> deliveries = deliveryRepository
                .findByStationIdAndDeliveryDateBetween(stationId, start, end);
        List<Tank> tanks = tankRepository.findByStationId(stationId);

        // ---- per-fuel aggregates, seeded from tanks ----
        Map<Long, MonthlyReportData.FuelStat> fuelStats = new LinkedHashMap<>();
        for (Tank tank : tanks) {
            Long fid = tank.getFuelType().getId();
            BigDecimal pct = tank.getCapacity().compareTo(BigDecimal.ZERO) > 0
                    ? tank.getCurrentStock().multiply(BigDecimal.valueOf(100))
                      .divide(tank.getCapacity(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            fuelStats.put(fid, MonthlyReportData.FuelStat.builder()
                    .fuelTypeId(fid)
                    .fuelTypeName(tank.getFuelType().getName())
                    .totalSales(BigDecimal.ZERO)
                    .totalDeliveries(BigDecimal.ZERO)
                    .avgDailySales(BigDecimal.ZERO)
                    .currentStock(tank.getCurrentStock())
                    .capacity(tank.getCapacity())
                    .stockPercentage(pct)
                    .build());
        }

        for (Sale s : sales) {
            MonthlyReportData.FuelStat fs = fuelStats.computeIfAbsent(s.getFuelType().getId(),
                    id -> emptyFuelStat(id, s.getFuelType().getName()));
            fs.setTotalSales(fs.getTotalSales().add(s.getQuantity()));
        }
        for (Delivery d : deliveries) {
            MonthlyReportData.FuelStat fs = fuelStats.computeIfAbsent(d.getFuelType().getId(),
                    id -> emptyFuelStat(id, d.getFuelType().getName()));
            fs.setTotalDeliveries(fs.getTotalDeliveries().add(d.getQuantity()));
        }
        int daysInMonth = ym.lengthOfMonth();
        for (MonthlyReportData.FuelStat fs : fuelStats.values()) {
            fs.setAvgDailySales(fs.getTotalSales().divide(
                    BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP));
        }

        // ---- daily series for charts ----
        List<MonthlyReportData.DailySeries> dailySales =
                buildDailySalesSeries(sales, start, end);
        List<MonthlyReportData.DailySeries> dailyDeliveries =
                buildDailyDeliveriesSeries(deliveries, start, end);

        // ---- tanks snapshot ----
        List<MonthlyReportData.TankSnapshot> tankSnaps = tanks.stream()
                .map(t -> {
                    BigDecimal pct = t.getCapacity().compareTo(BigDecimal.ZERO) > 0
                            ? t.getCurrentStock().multiply(BigDecimal.valueOf(100))
                              .divide(t.getCapacity(), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return MonthlyReportData.TankSnapshot.builder()
                            .tankId(t.getId())
                            .fuelTypeName(t.getFuelType().getName())
                            .capacity(t.getCapacity())
                            .currentStock(t.getCurrentStock())
                            .criticalThreshold(t.getCriticalThreshold())
                            .stockPercentage(pct)
                            .critical(t.getCurrentStock().compareTo(t.getCriticalThreshold()) <= 0)
                            .build();
                })
                .collect(Collectors.toList());

        // ---- alerts during the period (filter by createdAt) ----
        List<MonthlyReportData.AlertSummary> alertSummaries = alertRepository
                .findByStationId(stationId,
                        PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .filter(a -> a.getCreatedAt() != null
                        && !a.getCreatedAt().toLocalDate().isBefore(start)
                        && !a.getCreatedAt().toLocalDate().isAfter(end))
                .map(a -> MonthlyReportData.AlertSummary.builder()
                        .createdAt(a.getCreatedAt().toLocalDate())
                        .alertType(a.getAlertType().name())
                        .severity(a.getSeverity().name())
                        .message(a.getMessage())
                        .status(a.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        // ---- detailed tables ----
        List<MonthlyReportData.SaleSummary> salesTable = sales.stream()
                .map(s -> MonthlyReportData.SaleSummary.builder()
                        .saleDate(s.getSaleDate())
                        .fuelTypeName(s.getFuelType().getName())
                        .quantity(s.getQuantity())
                        .validated(Boolean.TRUE.equals(s.getValidated()))
                        .build())
                .collect(Collectors.toList());

        List<MonthlyReportData.DeliverySummary> delivTable = deliveries.stream()
                .map(d -> MonthlyReportData.DeliverySummary.builder()
                        .deliveryDate(d.getDeliveryDate())
                        .fuelTypeName(d.getFuelType().getName())
                        .quantity(d.getQuantity())
                        .validated(Boolean.TRUE.equals(d.getValidated()))
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalSales = sales.stream()
                .map(Sale::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeliveries = deliveries.stream()
                .map(Delivery::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeAlerts = alertRepository.countByStationIdAndStatus(stationId, AlertStatus.ACTIVE);

        return MonthlyReportData.builder()
                .stationId(stationId)
                .stationName(station.getName())
                .stationRegion(station.getRegion())
                .stationAddress(station.getAddress())
                .stationStatus(station.getStatus().name())
                .year(year)
                .month(month)
                .monthName(monthNameFrench(month))
                .startDate(start)
                .endDate(end)
                .generatedAt(LocalDate.now())
                .generatedBy(generatedBy)
                .totalSales(totalSales)
                .totalDeliveries(totalDeliveries)
                .activeAlertsAtEndOfPeriod(activeAlerts)
                .alertsCreatedDuringPeriod(alertSummaries.size())
                .tankCount(tanks.size())
                .fuelStats(new ArrayList<>(fuelStats.values()))
                .dailySalesByFuel(dailySales)
                .dailyDeliveriesByFuel(dailyDeliveries)
                .tanks(tankSnaps)
                .alerts(alertSummaries)
                .sales(salesTable)
                .deliveries(delivTable)
                .build();
    }

    @Override
    public byte[] generateMonthlyPdf(Long stationId, int year, int month, String generatedBy) {
        MonthlyReportData data = buildMonthlyReportData(stationId, year, month, generatedBy);
        try {
            return pdfGenerator.generate(data);
        } catch (IOException e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    @Override
    public byte[] generateMonthlyExcel(Long stationId, int year, int month, String generatedBy) {
        MonthlyReportData data = buildMonthlyReportData(stationId, year, month, generatedBy);
        try {
            return excelGenerator.generate(data);
        } catch (IOException e) {
            log.error("Excel generation failed", e);
            throw new RuntimeException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    // ===================== helpers =====================

    private MonthlyReportData.FuelStat emptyFuelStat(Long fid, String name) {
        return MonthlyReportData.FuelStat.builder()
                .fuelTypeId(fid)
                .fuelTypeName(name)
                .totalSales(BigDecimal.ZERO)
                .totalDeliveries(BigDecimal.ZERO)
                .avgDailySales(BigDecimal.ZERO)
                .currentStock(BigDecimal.ZERO)
                .capacity(BigDecimal.ZERO)
                .stockPercentage(BigDecimal.ZERO)
                .build();
    }

    private List<MonthlyReportData.DailySeries> buildDailySalesSeries(
            List<Sale> sales, LocalDate start, LocalDate end) {

        Map<Long, MonthlyReportData.DailySeries> map = new LinkedHashMap<>();
        for (Sale s : sales) {
            Long fid = s.getFuelType().getId();
            MonthlyReportData.DailySeries ds = map.computeIfAbsent(fid, id ->
                    MonthlyReportData.DailySeries.builder()
                            .fuelTypeId(fid)
                            .fuelTypeName(s.getFuelType().getName())
                            .points(initDailyZero(start, end))
                            .build());
            for (MonthlyReportData.DailyPoint p : ds.getPoints()) {
                if (p.getDate().equals(s.getSaleDate())) {
                    p.setValue(p.getValue().add(s.getQuantity()));
                    break;
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<MonthlyReportData.DailySeries> buildDailyDeliveriesSeries(
            List<Delivery> deliveries, LocalDate start, LocalDate end) {

        Map<Long, MonthlyReportData.DailySeries> map = new LinkedHashMap<>();
        for (Delivery d : deliveries) {
            Long fid = d.getFuelType().getId();
            MonthlyReportData.DailySeries ds = map.computeIfAbsent(fid, id ->
                    MonthlyReportData.DailySeries.builder()
                            .fuelTypeId(fid)
                            .fuelTypeName(d.getFuelType().getName())
                            .points(initDailyZero(start, end))
                            .build());
            for (MonthlyReportData.DailyPoint p : ds.getPoints()) {
                if (p.getDate().equals(d.getDeliveryDate())) {
                    p.setValue(p.getValue().add(d.getQuantity()));
                    break;
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<MonthlyReportData.DailyPoint> initDailyZero(LocalDate start, LocalDate end) {
        List<MonthlyReportData.DailyPoint> pts = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            pts.add(MonthlyReportData.DailyPoint.builder()
                    .date(cur).value(BigDecimal.ZERO).build());
            cur = cur.plusDays(1);
        }
        return pts;
    }

    private String monthNameFrench(int m) {
        String[] names = {"Janvier","Février","Mars","Avril","Mai","Juin",
                "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        return names[m - 1];
    }
}