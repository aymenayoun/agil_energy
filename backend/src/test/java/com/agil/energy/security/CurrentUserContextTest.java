package com.agil.energy.security;

import com.agil.energy.entity.Role;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.User;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.repository.StationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserContextTest {

    @Mock private StationRepository stationRepository;
    private CurrentUserContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new CurrentUserContext(stationRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String roleName, Long stationId, String region) {
        Role role = new Role(); role.setName(roleName);
        User user = new User();
        user.setId(1L); user.setName("Test"); user.setEmail("t@t.com");
        user.setStatus(UserStatus.ACTIVE); user.setRole(role);
        user.setRegion(region);
        if (stationId != null) {
            Station st = new Station(); st.setId(stationId); st.setName("St");
            st.setRegion("R"); st.setStatus(StationStatus.ACTIVE);
            user.setStation(st);
        }
        CustomUserDetails cud = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
    }

    @Test
    @DisplayName("isAdmin returns true for ADMIN")
    void isAdmin() {
        authenticate("ADMIN", null, null);
        assertThat(ctx.isAdmin()).isTrue();
        assertThat(ctx.isManager()).isFalse();
        assertThat(ctx.isStationManager()).isFalse();
    }

    @Test
    @DisplayName("isManager returns true for MANAGER")
    void isManager() {
        authenticate("MANAGER", null, "Nord");
        assertThat(ctx.isManager()).isTrue();
    }

    @Test
    @DisplayName("isStationManager returns true for STATION_MANAGER")
    void isStationManager() {
        authenticate("STATION_MANAGER", 5L, null);
        assertThat(ctx.isStationManager()).isTrue();
    }

    @Test
    @DisplayName("scopedStationId returns stationId for STATION_MANAGER")
    void scopedStationId_stationManager() {
        authenticate("STATION_MANAGER", 7L, null);
        assertThat(ctx.scopedStationId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("scopedStationId returns null for ADMIN")
    void scopedStationId_admin() {
        authenticate("ADMIN", null, null);
        assertThat(ctx.scopedStationId()).isNull();
    }

    @Test
    @DisplayName("scopedStationIds returns null (unscoped) for ADMIN")
    void scopedStationIds_admin() {
        authenticate("ADMIN", null, null);
        assertThat(ctx.scopedStationIds()).isNull();
    }

    @Test
    @DisplayName("scopedStationIds returns region stations for MANAGER")
    void scopedStationIds_manager() {
        authenticate("MANAGER", null, "Nord");
        Station s1 = new Station(); s1.setId(10L);
        Station s2 = new Station(); s2.setId(20L);
        when(stationRepository.findByRegion("Nord")).thenReturn(List.of(s1, s2));

        List<Long> ids = ctx.scopedStationIds();
        assertThat(ids).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("scopedStationIds returns singleton for STATION_MANAGER")
    void scopedStationIds_stationManager() {
        authenticate("STATION_MANAGER", 5L, null);
        assertThat(ctx.scopedStationIds()).containsExactly(5L);
    }

    @Test
    @DisplayName("scopedRegion returns region for MANAGER")
    void scopedRegion_manager() {
        authenticate("MANAGER", null, "Sud");
        assertThat(ctx.scopedRegion()).isEqualTo("Sud");
    }

    @Test
    @DisplayName("scopedRegion returns null for ADMIN")
    void scopedRegion_admin() {
        authenticate("ADMIN", null, null);
        assertThat(ctx.scopedRegion()).isNull();
    }

    @Test
    @DisplayName("requireAccessTo — ADMIN always allowed")
    void requireAccessTo_admin() {
        authenticate("ADMIN", null, null);
        assertThatCode(() -> ctx.requireAccessTo(99L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireAccessTo — STATION_MANAGER denied for wrong station")
    void requireAccessTo_stationManager_denied() {
        authenticate("STATION_MANAGER", 5L, null);
        assertThatThrownBy(() -> ctx.requireAccessTo(99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("requireAccessTo — STATION_MANAGER allowed for own station")
    void requireAccessTo_stationManager_allowed() {
        authenticate("STATION_MANAGER", 5L, null);
        assertThatCode(() -> ctx.requireAccessTo(5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireAccessTo — MANAGER allowed for station in their region")
    void requireAccessTo_manager_allowed() {
        authenticate("MANAGER", null, "Nord");
        Station st = new Station(); st.setId(10L); st.setRegion("Nord");
        when(stationRepository.findById(10L)).thenReturn(Optional.of(st));

        assertThatCode(() -> ctx.requireAccessTo(10L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireAccessTo — MANAGER denied for station outside region")
    void requireAccessTo_manager_denied() {
        authenticate("MANAGER", null, "Nord");
        Station st = new Station(); st.setId(10L); st.setRegion("Sud");
        when(stationRepository.findById(10L)).thenReturn(Optional.of(st));

        assertThatThrownBy(() -> ctx.requireAccessTo(10L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("resolveStationId — STATION_MANAGER always forced to own station")
    void resolveStationId_stationManager() {
        authenticate("STATION_MANAGER", 5L, null);
        assertThat(ctx.resolveStationId(99L)).isEqualTo(5L);
        assertThat(ctx.resolveStationId(null)).isEqualTo(5L);
    }

    @Test
    @DisplayName("resolveStationId — ADMIN passes through requested id")
    void resolveStationId_admin() {
        authenticate("ADMIN", null, null);
        assertThat(ctx.resolveStationId(42L)).isEqualTo(42L);
        assertThat(ctx.resolveStationId(null)).isNull();
    }

    @Test
    @DisplayName("principal returns null when not authenticated")
    void principal_null() {
        assertThat(ctx.principal()).isNull();
        assertThat(ctx.currentUserId()).isNull();
    }
}