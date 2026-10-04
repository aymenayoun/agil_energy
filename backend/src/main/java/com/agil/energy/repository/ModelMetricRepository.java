package com.agil.energy.repository;

import com.agil.energy.entity.ModelMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelMetricRepository extends JpaRepository<ModelMetric, Long> {

    List<ModelMetric> findByModelNameOrderByTrainedAtDesc(String modelName);

    List<ModelMetric> findByStationIdAndFuelTypeIdOrderByTrainedAtDesc(Long stationId, Long fuelTypeId);
}