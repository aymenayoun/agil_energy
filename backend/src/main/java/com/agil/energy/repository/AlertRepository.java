package com.agil.energy.repository;

import com.agil.energy.entity.Alert;
import com.agil.energy.enums.AlertStatus;
import com.agil.energy.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.agil.energy.enums.AlertType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);
    Page<Alert> findByStationId(Long stationId, Pageable pageable);
    Page<Alert> findByStatusAndAlertType(AlertStatus status, AlertType alertType, Pageable pageable);
    Page<Alert> findByStationIdAndAlertType(Long stationId, AlertType alertType, Pageable pageable);
    long countByStatus(AlertStatus status);
    long countByStationIdAndStatus(Long stationId, AlertStatus status);
    long countBySeverityAndStatus(Severity severity, AlertStatus status);
    long countByAlertType(AlertType alertType);
    boolean existsByStationIdAndAlertTypeAndStatusAndMessageContaining(
            Long stationId, AlertType alertType, AlertStatus status, String messageFragment);
    /** Count of alerts of a given type created since a cutoff. */
    long countByAlertTypeAndCreatedAtAfter(AlertType alertType, LocalDateTime since);

    /** Max absolute Z-score for anomaly alerts since cutoff. Null when no rows. */
    @Query("SELECT MAX(ABS(a.zScore)) FROM Alert a " +
            "WHERE a.alertType = :type " +
            "  AND a.zScore IS NOT NULL " +
            "  AND a.createdAt >= :since")
    Double findMaxAbsZScoreSince(
            @Param("type") AlertType type,
            @Param("since") LocalDateTime since);

    /**
     * Top stations by alert count since cutoff. Returns rows of
     * [stationId (Long), stationName (String), count (Long)] sorted desc.
     * Use Pageable.ofSize(1) to get only the top one.
     */
    @Query("SELECT a.station.id, a.station.name, COUNT(a) " +
            "FROM Alert a " +
            "WHERE a.alertType = :type AND a.createdAt >= :since " +
            "GROUP BY a.station.id, a.station.name " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> findTopStationsByAlertTypeSince(
            @Param("type") AlertType type,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    // ==================== Region-scoped queries (MANAGER) ====================

    /** Active alerts across multiple stations, paged. */
    Page<Alert> findByStationIdInAndStatus(List<Long> stationIds, AlertStatus status, Pageable pageable);

    /** Active alerts across multiple stations filtered by type, paged. */
    Page<Alert> findByStationIdInAndStatusAndAlertType(
            List<Long> stationIds, AlertStatus status, AlertType alertType, Pageable pageable);

    /** Count active alerts across multiple stations. */
    long countByStationIdInAndStatus(List<Long> stationIds, AlertStatus status);

    /** Count active alerts by severity across multiple stations. */
    long countByStationIdInAndSeverityAndStatus(List<Long> stationIds, Severity severity, AlertStatus status);
}