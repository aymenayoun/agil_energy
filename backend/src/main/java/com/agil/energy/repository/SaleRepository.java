package com.agil.energy.repository;

import com.agil.energy.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByStationIdAndFuelTypeIdAndSaleDate(Long stationId, Long fuelTypeId, LocalDate saleDate);

    List<Sale> findByStationIdAndFuelTypeIdAndSaleDateBetweenOrderBySaleDateAsc(
            Long stationId, Long fuelTypeId, LocalDate startDate, LocalDate endDate);

    List<Sale> findByStationIdAndSaleDateBetweenOrderBySaleDateAsc(
            Long stationId, LocalDate startDate, LocalDate endDate);

    List<Sale> findByStationIdOrderBySaleDateDesc(Long stationId);

    @Query("SELECT AVG(s.quantity) FROM Sale s " +
            "WHERE s.station.id = :stationId AND s.fuelType.id = :fuelTypeId " +
            "AND s.saleDate BETWEEN :startDate AND :endDate AND s.validated = true")
    BigDecimal findAverageDailyConsumption(
            @Param("stationId") Long stationId,
            @Param("fuelTypeId") Long fuelTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(s) FROM Sale s " +
            "WHERE s.station.id = :stationId AND s.fuelType.id = :fuelTypeId " +
            "AND s.saleDate = :saleDate")
    long countByStationAndFuelTypeAndDate(
            @Param("stationId") Long stationId,
            @Param("fuelTypeId") Long fuelTypeId,
            @Param("saleDate") LocalDate saleDate);

    boolean existsByStationIdAndFuelTypeIdAndSaleDate(Long stationId, Long fuelTypeId, LocalDate saleDate);

    @Query("SELECT COUNT(s), AVG(s.quantity), " +
            "  STDDEV_POP(s.quantity) " +
            "FROM Sale s " +
            "WHERE s.station.id = :stationId " +
            "  AND s.fuelType.id = :fuelTypeId " +
            "  AND s.validated = true " +
            "  AND s.saleDate BETWEEN :startDate AND :endDate")
    Object[] computeQuantityStats(
            @Param("stationId") Long stationId,
            @Param("fuelTypeId") Long fuelTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}