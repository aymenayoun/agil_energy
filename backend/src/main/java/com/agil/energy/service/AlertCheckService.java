package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.Tank;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.SaleRepository;
import com.agil.energy.repository.StationRepository;
import com.agil.energy.repository.TankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertCheckService {

    private final TankRepository tankRepository;
    private final StationRepository stationRepository;
    private final SaleRepository saleRepository;
    private final AlertRepository alertRepository;
    private final AlertPublisher alertPublisher;

    /**
     * Check all tanks for critical stock levels.
     * Called after every sale and delivery, and also runs on a schedule.
     */
    @Transactional
    public void checkStockAlerts(Long stationId, Long fuelTypeId) {
        Tank tank = tankRepository.findByStationIdAndFuelTypeId(stationId, fuelTypeId).orElse(null);
        if (tank == null) return;

        BigDecimal stock = tank.getCurrentStock();
        BigDecimal threshold = tank.getCriticalThreshold();
        String stationName = tank.getStation().getName();
        String fuelName = tank.getFuelType().getName();

        // STOCK_RUPTURE: stock below critical threshold
        if (stock.compareTo(threshold) <= 0) {
            BigDecimal pct = stock.multiply(BigDecimal.valueOf(100))
                    .divide(tank.getCapacity(), 1, RoundingMode.HALF_UP);

            // Check if there's already an active alert for this
            if (!hasActiveAlert(stationId, AlertType.STOCK_RUPTURE, fuelName)) {
                Severity severity = pct.compareTo(BigDecimal.TEN) <= 0 ? Severity.HIGH : Severity.MEDIUM;

                Alert alert = Alert.builder()
                        .station(tank.getStation())
                        .alertType(AlertType.STOCK_RUPTURE)
                        .severity(severity)
                        .message(String.format(
                                "Stock critique : %s a %s%% de capacité a %s. Stock actuel: %sL, seuil: %sL",
                                fuelName, pct, stationName, stock.setScale(0, RoundingMode.HALF_UP),
                                threshold.setScale(0, RoundingMode.HALF_UP)))
                        .status(AlertStatus.ACTIVE)
                        .build();
                alertPublisher.publish(alertRepository.save(alert));
                log.warn("[ALERTE] Stock critique: {} - {} a {}% ({}/{}L)",
                        stationName, fuelName, pct, stock, threshold);
            }
        }

        // STOCK_INCOHERENT: stock is negative (should never happen but safety check)
        if (stock.compareTo(BigDecimal.ZERO) < 0) {
            if (!hasActiveAlert(stationId, AlertType.STOCK_INCOHERENT, fuelName)) {
                Alert alert = Alert.builder()
                        .station(tank.getStation())
                        .alertType(AlertType.STOCK_INCOHERENT)
                        .severity(Severity.HIGH)
                        .message(String.format(
                                "Stock incoherent : %s a un stock négatif (%sL) a %s",
                                fuelName, stock, stationName))
                        .status(AlertStatus.ACTIVE)
                        .build();
                alertPublisher.publish(alertRepository.save(alert));
                log.error("[ALERTE] Stock incoherent: {} - {} = {}L", stationName, fuelName, stock);
            }
        }
    }

    /**
     * Daily scheduled check: run every day at 6:00 AM.
     * Checks all tanks for critical levels and missing entries.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Tunis")
    @Transactional
    public void dailyAlertCheck() {
        log.info("[AlertCheck] Verification quotidienne des alertes...");

        // Check all tanks for critical stock
        List<Tank> allTanks = tankRepository.findAll();
        int stockAlerts = 0;
        for (Tank tank : allTanks) {
            if (tank.getCurrentStock().compareTo(tank.getCriticalThreshold()) <= 0) {
                checkStockAlerts(tank.getStation().getId(), tank.getFuelType().getId());
                stockAlerts++;
            }
        }

        // Check for missing entries (stations with no sales yesterday)
        int missingAlerts = checkMissingEntries();

        log.info("[AlertCheck] Terminée: {} alertes stock, {} alertes saisie manquante",
                stockAlerts, missingAlerts);
    }

    /**
     * Check for stations that had no sales entry yesterday.
     * Generates MISSING_ENTRY alerts.
     */
    @Transactional
    public int checkMissingEntries() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Africa/Tunis")).minusDays(1);
        List<Station> activeStations = stationRepository.findByStatus(StationStatus.ACTIVE);
        int alertCount = 0;

        for (Station station : activeStations) {
            List<Tank> tanks = tankRepository.findByStationId(station.getId());
            for (Tank tank : tanks) {
                boolean hasSale = saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(
                        station.getId(), tank.getFuelType().getId(), yesterday);

                if (!hasSale) {
                    String fuelName = tank.getFuelType().getName();
                    if (!hasActiveAlert(station.getId(), AlertType.MISSING_ENTRY, fuelName)) {
                        Alert alert = Alert.builder()
                                .station(station)
                                .alertType(AlertType.MISSING_ENTRY)
                                .severity(Severity.LOW)
                                .message(String.format(
                                        "Absence de saisie : aucune vente enregistrée pour %s a %s le %s",
                                        fuelName, station.getName(), yesterday))
                                .status(AlertStatus.ACTIVE)
                                .build();
                        alertPublisher.publish(alertRepository.save(alert));
                        alertCount++;
                    }
                }
            }
        }
        return alertCount;
    }

    /**
     * Check if an active alert already exists to avoid duplicates.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasActiveAlert(Long stationId, AlertType alertType, String fuelName) {
        return alertRepository.existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
                stationId, alertType, AlertStatus.ACTIVE, fuelName);
    }
}