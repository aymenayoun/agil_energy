package com.agil.energy.security;

import com.agil.energy.entity.Station;
import com.agil.energy.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reads the current Spring Security principal and exposes ergonomic scoping helpers.
 *   - ADMIN           : no implicit filter; can access everything
 *   - MANAGER         : restricted to stations whose region matches their assigned region
 *   - STATION_MANAGER : restricted to their single assigned station
 */
@Component
@RequiredArgsConstructor
public class CurrentUserContext {

    public static final String ADMIN           = "ADMIN";
    public static final String MANAGER         = "MANAGER";
    public static final String STATION_MANAGER = "STATION_MANAGER";

    private final StationRepository stationRepository;

    /** Current principal, or null if anonymous / non-Custom. */
    public CustomUserDetails principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        return (p instanceof CustomUserDetails cud) ? cud : null;
    }

    public boolean isAdmin()          { return matchRole(ADMIN); }
    public boolean isManager()        { return matchRole(MANAGER); }
    public boolean isStationManager() { return matchRole(STATION_MANAGER); }

    // ==================== Station scoping ====================

    /**
     * Returns the principal's single station id when STATION_MANAGER, else null.
     * For MANAGER, use scopedStationIds() instead (multiple stations in region).
     */
    public Long scopedStationId() {
        CustomUserDetails p = principal();
        if (p == null) return null;
        if (STATION_MANAGER.equals(p.getRole())) return p.getStationId();
        return null;
    }

    /**
     * Returns all station ids within the principal's scope:
     *   - ADMIN           → null (no filter, caller should not filter)
     *   - MANAGER         → all station ids in their assigned region
     *   - STATION_MANAGER → singleton list with their station id
     */
    public List<Long> scopedStationIds() {
        CustomUserDetails p = principal();
        if (p == null) return Collections.emptyList();
        if (ADMIN.equals(p.getRole())) return null; // null = unscoped
        if (MANAGER.equals(p.getRole()) && p.getRegion() != null) {
            return stationRepository.findByRegion(p.getRegion()).stream()
                    .map(Station::getId)
                    .collect(Collectors.toList());
        }
        if (STATION_MANAGER.equals(p.getRole()) && p.getStationId() != null) {
            return List.of(p.getStationId());
        }
        return Collections.emptyList();
    }

    // ==================== Region scoping ====================

    /**
     * Returns the principal's region when MANAGER, else null.
     */
    public String scopedRegion() {
        CustomUserDetails p = principal();
        if (p == null) return null;
        if (MANAGER.equals(p.getRole())) return p.getRegion();
        return null;
    }

    // ==================== Access checks ====================

    /**
     * Resolve a requested stationId against the principle's scope.
     * STATION_MANAGER → always forced to their own station.
     * MANAGER         → accepts any station within their region, or defaults to null.
     * ADMIN           → passes through whatever was requested.
     */
    public Long resolveStationId(Long requested) {
        Long scoped = scopedStationId();
        return scoped != null ? scoped : requested;
    }

    /**
     * Authorize an operation against a target stationId.
     * - ADMIN           : always allowed.
     * - MANAGER         : allowed if the station belongs to their region.
     * - STATION_MANAGER : allowed only when stationId equals their own.
     */
    public void requireAccessTo(Long stationId) {
        CustomUserDetails p = principal();
        if (p == null) {
            throw new AccessDeniedException("Non authentifié");
        }
        if (ADMIN.equals(p.getRole())) {
            return;
        }
        if (MANAGER.equals(p.getRole())) {
            if (stationId == null) return; // no specific station targeted
            if (p.getRegion() == null) {
                throw new AccessDeniedException("Aucune région assignée à ce gestionnaire");
            }
            // Check the station belongs to the manager's region
            boolean belongs = stationRepository.findById(stationId)
                    .map(s -> p.getRegion().equals(s.getRegion()))
                    .orElse(false);
            if (!belongs) {
                throw new AccessDeniedException(
                        "Accès refusé: cette station n'appartient pas à votre région (" + p.getRegion() + ")");
            }
            return;
        }
        if (STATION_MANAGER.equals(p.getRole())) {
            if (stationId == null || p.getStationId() == null || !Objects.equals(p.getStationId(), stationId)) {
                throw new AccessDeniedException("Accès refusé: cette station n'est pas dans votre périmètre");
            }
            return;
        }
        throw new AccessDeniedException("Rôle non autorisé");
    }

    /** Current authenticated user id, or null. */
    public Long currentUserId() {
        CustomUserDetails p = principal();
        return p == null ? null : p.getId();
    }

    private boolean matchRole(String role) {
        CustomUserDetails p = principal();
        return p != null && role.equals(p.getRole());
    }
}