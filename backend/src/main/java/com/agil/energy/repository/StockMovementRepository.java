package com.agil.energy.repository;

import com.agil.energy.entity.StockMovement;
import com.agil.energy.enums.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByTankIdOrderByCreatedAtDesc(Long tankId);

    List<StockMovement> findByTankIdAndMovementType(Long tankId, MovementType movementType);

    List<StockMovement> findByTankIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long tankId, LocalDateTime startDate, LocalDateTime endDate);
}