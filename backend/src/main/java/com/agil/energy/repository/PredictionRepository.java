package com.agil.energy.repository;

import com.agil.energy.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    List<Prediction> findByStationIdAndFuelTypeIdAndPredictionDateBetweenOrderByPredictionDateAsc(
            Long stationId, Long fuelTypeId, LocalDate startDate, LocalDate endDate);

    List<Prediction> findByStationIdAndPredictionDateBetweenOrderByPredictionDateAsc(
            Long stationId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT p FROM Prediction p WHERE p.station.id = :stationId " +
            "AND p.fuelType.id = :fuelTypeId ORDER BY p.generatedAt DESC LIMIT 14")
    List<Prediction> findLatestPredictions(
            @Param("stationId") Long stationId,
            @Param("fuelTypeId") Long fuelTypeId);

    void deleteByStationIdAndFuelTypeIdAndPredictionDateBetween(
            Long stationId, Long fuelTypeId, LocalDate startDate, LocalDate endDate);
}