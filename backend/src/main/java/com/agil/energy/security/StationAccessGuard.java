package com.agil.energy.security;

import com.agil.energy.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * SpEL helper bean for @PreAuthorize annotations:
 *   @PreAuthorize("hasRole('ADMIN') or @stationAccess.canAccess(authentication, #stationId)")
 * Returns true when:
 *   - ADMIN : always
 *   - MANAGER : station belongs to their region
 *   - STATION_MANAGER : stationId matches their assigned station
 */
@Component("stationAccess")
@RequiredArgsConstructor
public class StationAccessGuard {

    private final StationRepository stationRepository;

    public boolean canAccess(Authentication authentication, Long stationId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails p)) {
            return false;
        }
        return canAccess(p, stationId);
    }

    public boolean canAccess(CustomUserDetails principal, Long stationId) {
        if (principal == null) return false;
        if ("ADMIN".equals(principal.getRole())) return true;

        if ("MANAGER".equals(principal.getRole())) {
            if (stationId == null || principal.getRegion() == null) return false;
            return stationRepository.findById(stationId)
                    .map(s -> principal.getRegion().equals(s.getRegion()))
                    .orElse(false);
        }

        if ("STATION_MANAGER".equals(principal.getRole())) {
            if (stationId == null || principal.getStationId() == null) return false;
            return Objects.equals(principal.getStationId(), stationId);
        }

        return false;
    }
}