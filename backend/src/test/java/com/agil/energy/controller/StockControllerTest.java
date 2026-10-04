package com.agil.energy.controller;

import com.agil.energy.dto.request.StockAdjustmentRequest;
import com.agil.energy.dto.response.StockMovementResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockControllerTest {

    @Mock private StockService stockService;
    @InjectMocks private StockController controller;

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
    @DisplayName("getStocksByStation — returns 200")
    void getStocksByStation_returns200() {
        when(stockService.getStocksByStation(4L)).thenReturn(List.of(new TankResponse()));

        var response = controller.getStocksByStation(4L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("adjustStock — returns 200")
    void adjustStock_returns200() {
        TankResponse mock = new TankResponse(); mock.setId(2L);
        when(stockService.adjustStock(any(), eq(1L))).thenReturn(mock);

        var response = controller.adjustStock(new StockAdjustmentRequest(), currentUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getCriticalTanks — returns 200")
    void getCriticalTanks_returns200() {
        when(stockService.getCriticalTanks()).thenReturn(List.of());

        var response = controller.getCriticalTanks();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    @DisplayName("getMovements — returns 200")
    void getMovements_returns200() {
        when(stockService.getMovementsByTank(7L)).thenReturn(List.of(new StockMovementResponse()));

        var response = controller.getMovements(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }
}