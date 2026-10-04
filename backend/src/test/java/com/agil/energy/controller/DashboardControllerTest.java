package com.agil.energy.controller;

import com.agil.energy.dto.response.DashboardResponse;
import com.agil.energy.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private DashboardService dashboardService;
    @InjectMocks private DashboardController controller;

    @Test
    @DisplayName("getDashboard — no stationId returns 200")
    void getDashboard_noStation_returns200() {
        DashboardResponse mock = new DashboardResponse();
        when(dashboardService.getDashboard(null)).thenReturn(mock);

        var response = controller.getDashboard(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(dashboardService).getDashboard(null);
    }

    @Test
    @DisplayName("getDashboard — with stationId passes it through")
    void getDashboard_withStation_passesId() {
        DashboardResponse mock = new DashboardResponse();
        when(dashboardService.getDashboard(5L)).thenReturn(mock);

        var response = controller.getDashboard(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(dashboardService).getDashboard(5L);
    }
}