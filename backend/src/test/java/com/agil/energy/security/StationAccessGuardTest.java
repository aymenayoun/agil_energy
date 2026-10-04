package com.agil.energy.security;

import com.agil.energy.entity.Role;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.User;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.repository.StationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationAccessGuardTest {

    @Mock private StationRepository stationRepository;
    @InjectMocks private StationAccessGuard guard;

    private CustomUserDetails makePrincipal(String roleName, Long stationId, String region) {
        Role role = new Role(); role.setName(roleName);
        User user = new User();
        user.setId(1L); user.setName("T"); user.setEmail("t@t.com");
        user.setStatus(UserStatus.ACTIVE); user.setRole(role);
        user.setRegion(region);
        if (stationId != null) {
            Station st = new Station(); st.setId(stationId); st.setName("St");
            st.setRegion("R"); st.setStatus(StationStatus.ACTIVE);
            user.setStation(st);
        }
        return new CustomUserDetails(user);
    }

    @Test
    @DisplayName("ADMIN can access any station")
    void admin_canAccessAny() {
        assertThat(guard.canAccess(makePrincipal("ADMIN", null, null), 99L)).isTrue();
    }

    @Test
    @DisplayName("STATION_MANAGER can access own station")
    void stationManager_ownStation() {
        assertThat(guard.canAccess(makePrincipal("STATION_MANAGER", 5L, null), 5L)).isTrue();
    }

    @Test
    @DisplayName("STATION_MANAGER denied for other station")
    void stationManager_otherStation() {
        assertThat(guard.canAccess(makePrincipal("STATION_MANAGER", 5L, null), 99L)).isFalse();
    }

    @Test
    @DisplayName("MANAGER can access station in their region")
    void manager_sameRegion() {
        Station st = new Station(); st.setId(10L); st.setRegion("Nord");
        when(stationRepository.findById(10L)).thenReturn(Optional.of(st));

        assertThat(guard.canAccess(makePrincipal("MANAGER", null, "Nord"), 10L)).isTrue();
    }

    @Test
    @DisplayName("MANAGER denied for station outside their region")
    void manager_differentRegion() {
        Station st = new Station(); st.setId(10L); st.setRegion("Sud");
        when(stationRepository.findById(10L)).thenReturn(Optional.of(st));

        assertThat(guard.canAccess(makePrincipal("MANAGER", null, "Nord"), 10L)).isFalse();
    }

    @Test
    @DisplayName("null principal returns false")
    void nullPrincipal() {
        assertThat(guard.canAccess((CustomUserDetails) null, 1L)).isFalse();
    }

    @Test
    @DisplayName("null stationId returns false for STATION_MANAGER")
    void nullStationId_stationManager() {
        assertThat(guard.canAccess(makePrincipal("STATION_MANAGER", 5L, null), null)).isFalse();
    }

    @Test
    @DisplayName("null stationId returns false for MANAGER")
    void nullStationId_manager() {
        assertThat(guard.canAccess(makePrincipal("MANAGER", null, "Nord"), null)).isFalse();
    }
}