package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateDeliveryRequest;
import com.agil.energy.dto.response.DeliveryResponse;
import com.agil.energy.service.DeliveryService;
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
class DeliveryControllerTest {

    @Mock private DeliveryService deliveryService;
    @InjectMocks private DeliveryController controller;

    @Test
    @DisplayName("createDelivery — returns 201")
    void createDelivery_returns201() {
        DeliveryResponse mock = new DeliveryResponse(); mock.setId(1L);
        when(deliveryService.createDelivery(any())).thenReturn(mock);

        var response = controller.createDelivery(new CreateDeliveryRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getDeliveries — no stationId returns all")
    void getDeliveries_noStation_returnsAll() {
        when(deliveryService.getAllDeliveries()).thenReturn(List.of(new DeliveryResponse()));

        var response = controller.getDeliveries(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        verify(deliveryService).getAllDeliveries();
        verify(deliveryService, never()).getDeliveriesByStation(any());
    }

    @Test
    @DisplayName("getDeliveries — with stationId filters")
    void getDeliveries_withStation_filters() {
        when(deliveryService.getDeliveriesByStation(3L)).thenReturn(List.of());

        var response = controller.getDeliveries(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(deliveryService).getDeliveriesByStation(3L);
    }

    @Test
    @DisplayName("validateDelivery — returns 200")
    void validateDelivery_returns200() {
        DeliveryResponse mock = new DeliveryResponse(); mock.setId(7L);
        when(deliveryService.validateDelivery(7L)).thenReturn(mock);

        var response = controller.validateDelivery(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(7L);
    }
}