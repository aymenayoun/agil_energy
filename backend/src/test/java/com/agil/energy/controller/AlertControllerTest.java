package com.agil.energy.controller;

import com.agil.energy.dto.response.AlertResponse;
import com.agil.energy.dto.response.AnomalyStatsResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    @Mock private AlertService alertService;
    @InjectMocks private AlertController controller;

    private CustomUserDetails currentUser;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setName("ADMIN");
        User user = new User();
        user.setId(1L); user.setName("Admin"); user.setEmail("admin@x.com");
        user.setStatus(UserStatus.ACTIVE); user.setRole(role);
        currentUser = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("getActiveAlerts — returns 200 with paged alerts")
    void getActiveAlerts_returns200() {
        Page<AlertResponse> page = new PageImpl<>(List.of(new AlertResponse()));
        when(alertService.getActiveAlerts(any(), any(Pageable.class))).thenReturn(page);

        var response = controller.getActiveAlerts(null, Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getAlertsByStation — returns 200")
    void getAlertsByStation_returns200() {
        Page<AlertResponse> page = new PageImpl<>(List.of());
        when(alertService.getAlertsByStation(eq(5L), any(), any(Pageable.class))).thenReturn(page);

        var response = controller.getAlertsByStation(5L, null, Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(alertService).getAlertsByStation(eq(5L), any(), any());
    }

    @Test
    @DisplayName("resolveAlert — returns 200 and calls service")
    void resolveAlert_returns200() {
        AlertResponse mock = new AlertResponse(); mock.setId(10L);
        when(alertService.resolveAlert(10L, 1L)).thenReturn(mock);

        var response = controller.resolveAlert(10L, currentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("countActiveAlerts — returns count")
    void countActiveAlerts_returnsCount() {
        when(alertService.countActiveAlerts()).thenReturn(42L);

        var response = controller.countActiveAlerts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getAnomalyStats — returns stats")
    void getAnomalyStats_returnsStats() {
        AnomalyStatsResponse stats = new AnomalyStatsResponse();
        when(alertService.getAnomalyStats()).thenReturn(stats);

        var response = controller.getAnomalyStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isNotNull();
    }
}