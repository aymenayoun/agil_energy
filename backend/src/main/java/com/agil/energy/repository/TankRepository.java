package com.agil.energy.repository;

import com.agil.energy.entity.Tank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TankRepository extends JpaRepository<Tank, Long> {

    List<Tank> findByStationId(Long stationId);

    Optional<Tank> findByStationIdAndFuelTypeId(Long stationId, Long fuelTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tank t WHERE t.station.id = :stationId AND t.fuelType.id = :fuelTypeId")
    Optional<Tank> findByStationIdAndFuelTypeIdForUpdate(
            @Param("stationId") Long stationId,
            @Param("fuelTypeId") Long fuelTypeId);

    @Query("SELECT t FROM Tank t WHERE t.currentStock <= t.criticalThreshold")
    List<Tank> findTanksBelowThreshold();

    @Query("SELECT t FROM Tank t WHERE t.station.id = :stationId AND t.currentStock <= t.criticalThreshold")
    List<Tank> findCriticalTanksByStation(@Param("stationId") Long stationId);

    /** Critical tanks across multiple stations (region-scoped). */
    @Query("SELECT t FROM Tank t WHERE t.station.id IN :stationIds AND t.currentStock <= t.criticalThreshold")
    List<Tank> findCriticalTanksByStationIdIn(@Param("stationIds") List<Long> stationIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tank t WHERE t.id = :id")
    Optional<Tank> findByIdForUpdate(@Param("id") Long id);

    boolean existsByFuelTypeId(Long fuelTypeId);
}