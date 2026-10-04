package com.agil.energy.repository;

import com.agil.energy.entity.Station;
import com.agil.energy.enums.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, Long> {

    List<Station> findByStatus(StationStatus status);

    List<Station> findByRegion(String region);

    List<Station> findByRegionAndStatus(String region, StationStatus status);

    /**
     * Stations whose active manager is the given user id.
     * Replaces the old findByManagerId now that manager lives on users.station_id.
     */
    @Query("SELECT s FROM Station s JOIN s.assignedUsers u " +
            "WHERE u.id = :managerId AND u.role.name = 'STATION_MANAGER' AND u.status = 'ACTIVE'")
    List<Station> findStationsManagedBy(@Param("managerId") Long managerId);

    /** Filter stations by a list of IDs (used for region-scoped queries). */
    List<Station> findByIdIn(List<Long> ids);

    /** Active stations within a given set of IDs. */
    List<Station> findByStatusAndIdIn(StationStatus status, List<Long> ids);
}