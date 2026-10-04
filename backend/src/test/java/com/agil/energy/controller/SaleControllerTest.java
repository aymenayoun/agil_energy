package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateSaleRequest;
import com.agil.energy.dto.response.SaleResponse;
import com.agil.energy.service.SaleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleControllerTest {

    @Mock private SaleService saleService;
    @InjectMocks private SaleController controller;

    @Test
    @DisplayName("createSale — returns 201")
    void createSale_returns201() {
        SaleResponse mock = new SaleResponse(); mock.setId(1L);
        when(saleService.createSale(any())).thenReturn(mock);

        var response = controller.createSale(new CreateSaleRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getSaleById — returns 200")
    void getSaleById_returns200() {
        SaleResponse mock = new SaleResponse(); mock.setId(5L);
        when(saleService.getSaleById(5L)).thenReturn(mock);

        var response = controller.getSaleById(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getSales — station only calls getSalesByStation")
    void getSales_stationOnly() {
        when(saleService.getSalesByStation(1L)).thenReturn(List.of());

        var response = controller.getSales(1L, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(saleService).getSalesByStation(1L);
    }

    @Test
    @DisplayName("getSales — with dates calls dateRange method")
    void getSales_withDates() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        when(saleService.getSalesByStationAndDateRange(1L, start, end)).thenReturn(List.of());

        var response = controller.getSales(1L, null, start, end);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(saleService).getSalesByStationAndDateRange(1L, start, end);
    }

    @Test
    @DisplayName("getSales — with dates and fuelTypeId calls full filter")
    void getSales_withDatesAndFuel() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        when(saleService.getSalesByStationFuelAndDateRange(1L, 2L, start, end)).thenReturn(List.of());

        var response = controller.getSales(1L, 2L, start, end);

        verify(saleService).getSalesByStationFuelAndDateRange(1L, 2L, start, end);
    }

    @Test
    @DisplayName("validateSale — returns 200")
    void validateSale_returns200() {
        SaleResponse mock = new SaleResponse(); mock.setId(9L);
        when(saleService.validateSale(9L)).thenReturn(mock);

        var response = controller.validateSale(9L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(9L);
    }
}