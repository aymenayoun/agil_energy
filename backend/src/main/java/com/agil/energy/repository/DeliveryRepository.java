package com.agil.energy.repository;

import com.agil.energy.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByStationIdOrderByDeliveryDateDesc(Long stationId);

    List<Delivery> findByStationIdAndFuelTypeIdOrderByDeliveryDateDesc(Long stationId, Long fuelTypeId);

    List<Delivery> findByStationIdAndDeliveryDateBetween(Long stationId, LocalDate startDate, LocalDate endDate);

    /** Deliveries across multiple stations (region-scoped), newest first. */
    List<Delivery> findByStationIdInOrderByDeliveryDateDesc(List<Long> stationIds);
}