package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateStationRequest;
import com.agil.energy.dto.request.CreateTankRequest;
import com.agil.energy.dto.request.UpdateStationRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.StationResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationControllerTest {

    @Mock private StationService stationService;
    @Mock private AuditService auditService;
    @InjectMocks private StationController controller;

    private CustomUserDetails currentUser;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setName("ADMIN");
        User user = new User();
        user.setId(1L); user.setName("Admin"); user.setEmail("admin@x.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        currentUser = new CustomUserDetails(user);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
    }

    @Test
    @DisplayName("createStation — returns 201 + audits CREATE")
    void createStation_returns201() {
        CreateStationRequest req = new CreateStationRequest();
        StationResponse mock = new StationResponse(); mock.setId(99L); mock.setName("S1");
        when(stationService.createStation(any())).thenReturn(mock);

        ResponseEntity<ApiResponse<StationResponse>> response =
                controller.createStation(req, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(99L);
        verify(auditService).log(eq(1L), eq("CREATE"), eq("STATION"), eq(99L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("getAllStations — no region filter returns all")
    void getAllStations_noRegion_returnsAll() {
        when(stationService.getAllStations()).thenReturn(List.of(new StationResponse()));

        ResponseEntity<ApiResponse<List<StationResponse>>> response =
                controller.getAllStations(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        verify(stationService).getAllStations();
        verify(stationService, never()).getStationsByRegion(any());
    }

    @Test
    @DisplayName("getAllStations — region filter calls byRegion")
    void getAllStations_withRegion_filtersByRegion() {
        when(stationService.getStationsByRegion("Tunis")).thenReturn(List.of(new StationResponse()));

        ResponseEntity<ApiResponse<List<StationResponse>>> response =
                controller.getAllStations("Tunis");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stationService).getStationsByRegion("Tunis");
    }

    @Test
    @DisplayName("getStationById — returns 200 with station")
    void getStationById_returnsStation() {
        StationResponse mock = new StationResponse(); mock.setId(5L);
        when(stationService.getStationById(5L)).thenReturn(mock);

        ResponseEntity<ApiResponse<StationResponse>> response = controller.getStationById(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("updateStation — returns 200 + audits UPDATE")
    void updateStation_returns200() {
        UpdateStationRequest req = new UpdateStationRequest();
        StationResponse mock = new StationResponse(); mock.setId(5L);
        when(stationService.updateStation(eq(5L), any())).thenReturn(mock);

        ResponseEntity<ApiResponse<StationResponse>> response =
                controller.updateStation(5L, req, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auditService).log(eq(1L), eq("UPDATE"), eq("STATION"), eq(5L), any(), anyString());
    }

    @Test
    @DisplayName("deactivateStation — returns 200 + audits DEACTIVATE")
    void deactivateStation_returns200() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.deactivateStation(5L, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stationService).deactivateStation(5L);
        verify(auditService).log(eq(1L), eq("DEACTIVATE"), eq("STATION"), eq(5L), any(), anyString());
    }

    @Test
    @DisplayName("activateStation — returns 200 + audits ACTIVATE")
    void activateStation_returns200() {
        ResponseEntity<ApiResponse<Void>> response =
                controller.activateStation(5L, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(stationService).activateStation(5L);
        verify(auditService).log(eq(1L), eq("ACTIVATE"), eq("STATION"), eq(5L), any(), anyString());
    }

    @Test
    @DisplayName("addTank — returns 201 + audits CREATE TANK")
    void addTank_returns201() {
        CreateTankRequest req = new CreateTankRequest();
        TankResponse mock = new TankResponse(); mock.setId(11L);
        when(stationService.addTank(any())).thenReturn(mock);

        ResponseEntity<ApiResponse<TankResponse>> response =
                controller.addTank(req, currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(11L);
        verify(auditService).log(eq(1L), eq("CREATE"), eq("TANK"), eq(11L), any(), anyString());
    }
}