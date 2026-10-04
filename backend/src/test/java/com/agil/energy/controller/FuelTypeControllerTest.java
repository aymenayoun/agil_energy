package com.agil.energy.controller;

import com.agil.energy.dto.request.CreateFuelTypeRequest;
import com.agil.energy.dto.request.UpdateFuelTypeRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.entity.FuelType;
import com.agil.energy.service.FuelTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuelTypeControllerTest {

    @Mock private FuelTypeService fuelTypeService;
    @InjectMocks private FuelTypeController controller;

    @Test
    @DisplayName("getAllFuelTypes — returns 200 with list")
    void getAllFuelTypes_returns200() {
        FuelType ft = new FuelType(); ft.setId(1L); ft.setName("Diesel");
        when(fuelTypeService.getAllFuelTypes()).thenReturn(List.of(ft));

        ResponseEntity<ApiResponse<List<FuelType>>> response = controller.getAllFuelTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getAllFuelTypes — empty list returns success")
    void getAllFuelTypes_emptyList() {
        when(fuelTypeService.getAllFuelTypes()).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<FuelType>>> response = controller.getAllFuelTypes();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    @DisplayName("getFuelTypeById — returns 200 with fuel type")
    void getFuelTypeById_returns200() {
        FuelType ft = new FuelType(); ft.setId(5L); ft.setName("SP95");
        when(fuelTypeService.getFuelTypeById(5L)).thenReturn(ft);

        ResponseEntity<ApiResponse<FuelType>> response = controller.getFuelTypeById(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("createFuelType — returns 201 with created fuel type")
    void createFuelType_returns201() {
        CreateFuelTypeRequest req = new CreateFuelTypeRequest();
        req.setName("Gasoil");
        FuelType created = new FuelType(); created.setId(99L); created.setName("Gasoil");
        when(fuelTypeService.createFuelType(any())).thenReturn(created);

        ResponseEntity<ApiResponse<FuelType>> response = controller.createFuelType(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getId()).isEqualTo(99L);
        verify(fuelTypeService).createFuelType(req);
    }

    @Test
    @DisplayName("updateFuelType — returns 200 with updated fuel type")
    void updateFuelType_returns200() {
        UpdateFuelTypeRequest req = new UpdateFuelTypeRequest();
        req.setName("Gasoil 50");
        FuelType updated = new FuelType(); updated.setId(5L); updated.setName("Gasoil 50");
        when(fuelTypeService.updateFuelType(eq(5L), any())).thenReturn(updated);

        ResponseEntity<ApiResponse<FuelType>> response = controller.updateFuelType(5L, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getName()).isEqualTo("Gasoil 50");
        verify(fuelTypeService).updateFuelType(5L, req);
    }

    @Test
    @DisplayName("deleteFuelType — returns 200 + delegates to service")
    void deleteFuelType_returns200() {
        ResponseEntity<ApiResponse<Void>> response = controller.deleteFuelType(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(fuelTypeService).deleteFuelType(7L);
    }
}