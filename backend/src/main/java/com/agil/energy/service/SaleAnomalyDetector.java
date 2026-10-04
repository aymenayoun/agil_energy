package com.agil.energy.service;

import com.agil.energy.entity.Alert;
import com.agil.energy.entity.Sale;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.AlertType;
import com.agil.energy.enums.Severity;
import com.agil.energy.repository.AlertRepository;
import com.agil.energy.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Statistical anomaly detection performed synchronously at sale-creation time.

 * Computes mean (μ) and population stddev (σ) of validated sale quantities for
 * the station/fuel over the last WINDOW_DAYS days. Raises a SALE_ANOMALY alert
 * when the new quantity is more than Z_THRESHOLD standard deviations away from
 * the mean.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleAnomalyDetector {

    private static final int WINDOW_DAYS = 90;
    private static final int MIN_SAMPLES = 20;     // need enough history to trust σ
    private static final double Z_THRESHOLD = 2.0; // classic 2σ rule
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private final SaleRepository saleRepository;
    private final AlertRepository alertRepository;
    private final AlertPublisher alertPublisher;

    /**
     * Detect anomaly for the given sale. Never throws — any internal failure is
     * logged and swallowed so anomaly detection can never break a sale.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void detect(Sale sale) {
        try {
            LocalDate end = LocalDate.now(TUNIS).minusDays(1);
            LocalDate start = end.minusDays(WINDOW_DAYS);

            Object[] row = saleRepository.computeQuantityStats(
                    sale.getStation().getId(),
                    sale.getFuelType().getId(),
                    start, end);

            // Hibernate may wrap the single row inside another array
            if (row != null && row.length == 1 && row[0] instanceof Object[] inner) {
                row = inner;
            }
            if (row == null || row[0] == null) return;

            long count = ((Number) row[0]).longValue();
            if (count < MIN_SAMPLES) {
                log.debug("Anomaly check skipped: only {} historical samples (need {})",
                        count, MIN_SAMPLES);
                return;
            }

            double mean   = ((Number) row[1]).doubleValue();
            double stddev = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            if (stddev < 1e-6) return; // degenerate — all sales identical

            double qty = sale.getQuantity().doubleValue();
            double z   = (qty - mean) / stddev;

            if (Math.abs(z) > Z_THRESHOLD) {
                raiseAlert(sale, qty, mean, stddev, z);
            }
        } catch (Exception e) {
            log.warn("Anomaly detection failed for sale {}: {}", sale.getId(), e.getMessage());
        }
    }

    private void raiseAlert(Sale sale, double qty, double mean, double stddev, double z) {
        Severity severity = Math.abs(z) > 3.0 ? Severity.HIGH : Severity.MEDIUM;
        String direction = z > 0 ? "anormalement élevée" : "anormalement basse";
        double low  = mean - Z_THRESHOLD * stddev;
        double high = mean + Z_THRESHOLD * stddev;

        String message = String.format(
                Locale.ROOT,
                "Vente %s détectée : %s — %s (%.1fL). "
                        + "Moyenne 90j : %.1fL, plage normale : [%.1fL ; %.1fL], Z-score : %.2fσ.",
                direction,
                sale.getStation().getName(),
                sale.getFuelType().getName(),
                qty, mean,
                Math.max(0, low), high,
                z);

        Alert alert = Alert.builder()
                .station(sale.getStation())
                .alertType(AlertType.SALE_ANOMALY)
                .severity(severity)
                .message(message)
                .status(AlertStatus.ACTIVE)
                .zScore(z)
                .build();
        alertPublisher.publish(alertRepository.save(alert));

        log.warn("[SALE_ANOMALY] station={} fuel={} qty={} z={} severity={}",
                sale.getStation().getName(), sale.getFuelType().getName(),
                BigDecimal.valueOf(qty).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(z).setScale(2, RoundingMode.HALF_UP),
                severity);
    }
}