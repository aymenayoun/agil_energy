package com.agil.energy.repository;

import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRoleName(String roleName);

    /** All users currently scoped to a given station (any role, any status). */
    List<User> findByStationId(Long stationId);

    /** Active STATION_MANAGER of a station, if one is assigned. */
    Optional<User> findFirstByStationIdAndRoleNameAndStatus(Long stationId, String roleName, UserStatus status);
}