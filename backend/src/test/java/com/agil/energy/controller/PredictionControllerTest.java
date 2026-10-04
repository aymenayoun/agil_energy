package com.agil.energy.controller;

import com.agil.energy.dto.request.GeneratePredictionRequest;
import com.agil.energy.dto.request.GenerateRegionPredictionRequest;
import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.dto.response.PredictionResponse;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.IAClientService;
import com.agil.energy.service.PredictionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    @Mock private PredictionService predictionService;
    @Mock private IAClientService iaClientService;
    @Mock private CurrentUserContext ctx;
    @InjectMocks private PredictionController controller;

    @Test
    @DisplayName("getPredictions — returns 200 with predictions list")
    void getPredictions_returnsOk() {
        when(predictionService.getPredictions(1L, 1L))
                .thenReturn(List.of(new PredictionResponse()));

        ResponseEntity<ApiResponse<List<PredictionResponse>>> response =
                controller.getPredictions(1L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getLatestPredictions — returns 200")
    void getLatestPredictions_returnsOk() {
        when(predictionService.getLatestPredictions(1L, 1L))
                .thenReturn(List.of(new PredictionResponse(), new PredictionResponse()));

        ResponseEntity<ApiResponse<List<PredictionResponse>>> response =
                controller.getLatestPredictions(1L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    @DisplayName("generatePrediction — calls IA service with horizon=7")
    void generatePrediction_returnsOk() {
        GeneratePredictionRequest req = new GeneratePredictionRequest();
        req.setStationId(1L); req.setFuelTypeId(1L);
        when(iaClientService.generatePrediction(1L, 1L, 7))
                .thenReturn(Map.of("predictions", List.of(100, 110)));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.generatePrediction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("générées");
    }

    @Test
    @DisplayName("generateBatchPredictions — calls IA batch")
    void generateBatchPredictions_returnsOk() {
        when(iaClientService.generateBatchPredictions(7))
                .thenReturn(Map.of("count", 5));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.generateBatchPredictions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("checkIAHealth — returns 'available' when healthy")
    void checkIAHealth_healthy() {
        when(iaClientService.isHealthy()).thenReturn(true);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.checkIAHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().get("ia_service")).isEqualTo("available");
    }

    @Test
    @DisplayName("checkIAHealth — returns 'unavailable' when unhealthy")
    void checkIAHealth_unhealthy() {
        when(iaClientService.isHealthy()).thenReturn(false);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.checkIAHealth();

        assertThat(response.getBody().getData().get("ia_service")).isEqualTo("unavailable");
    }

    @Test
    @DisplayName("getModelMetrics — returns 200")
    void getModelMetrics_returnsOk() {
        when(iaClientService.getModelMetrics(1L, 1L)).thenReturn(Map.of("mape", 5.2));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getModelMetrics(1L, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getRegions — returns list of regions")
    void getRegions_returnsList() {
        // Simulate ADMIN (scopedRegion = null)
        when(ctx.scopedRegion()).thenReturn(null);
        when(iaClientService.getRegions()).thenReturn(List.of(Map.of("name", "Tunis")));

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = controller.getRegions();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getRegionFuelTypes — returns fuel types for region")
    void getRegionFuelTypes_returnsList() {
        // scopedRegion = null means ADMIN, no restriction
        when(ctx.scopedRegion()).thenReturn(null);
        when(iaClientService.getRegionFuelTypes("Tunis"))
                .thenReturn(List.of(Map.of("name", "Gasoil")));

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response =
                controller.getRegionFuelTypes("Tunis");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("generateRegionPrediction — returns 200")
    void generateRegionPrediction_returnsOk() {
        // scopedRegion = null means ADMIN
        when(ctx.scopedRegion()).thenReturn(null);
        GenerateRegionPredictionRequest req = new GenerateRegionPredictionRequest();
        req.setRegion("Tunis"); req.setFuelTypeId(1L);
        when(iaClientService.generateRegionPrediction(eq("Tunis"), eq(1L), anyInt()))
                .thenReturn(Map.of("forecast", List.of(100)));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.generateRegionPrediction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("régionales");
    }
}