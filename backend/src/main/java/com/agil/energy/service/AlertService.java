package com.agil.energy.service;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.agil.energy.dto.response.AnomalyStatsResponse;

public interface AlertService {
    /** @param alertType optional — when null, returns all types. */
    Page<AlertResponse> getActiveAlerts(AlertType alertType, Pageable pageable);

    /** @param alertType optional — when null, returns all types. */
    Page<AlertResponse> getAlertsByStation(Long stationId, AlertType alertType, Pageable pageable);

    AlertResponse resolveAlert(Long alertId, Long userId);
    long countActiveAlerts();
    AnomalyStatsResponse getAnomalyStats();
}