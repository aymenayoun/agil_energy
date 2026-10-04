package com.agil.energy.service;

import com.agil.energy.service.IAClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IAClientServiceTest {

    @Mock private RestTemplate restTemplate;

    @InjectMocks private IAClientService iaClientService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(iaClientService, "iaServiceUrl", "http://localhost:5000");
        ReflectionTestUtils.setField(iaClientService, "iaApiKey", "test-key");
    }

    @Test
    @DisplayName("isHealthy — returns true when IA service responds 2xx")
    void isHealthy_serviceUp_returnsTrue() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "healthy")));

        assertThat(iaClientService.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("isHealthy — returns false when IA service is down")
    void isHealthy_serviceDown_returnsFalse() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThat(iaClientService.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("generatePrediction — returns error map when IA service throws")
    void generatePrediction_serviceDown_returnsErrorMap() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = iaClientService.generatePrediction(1L, 1L, 7);

        assertThat(result).containsKey("status");
        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result).containsKey("message");
    }

    @Test
    @DisplayName("generatePrediction — returns body on successful call")
    void generatePrediction_success_returnsBody() {
        Map<String, Object> mockBody = Map.of("status", "success", "forecast_7_days", java.util.List.of(100.0));
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(mockBody));

        Map<String, Object> result = iaClientService.generatePrediction(1L, 1L, 7);

        assertThat(result.get("status")).isEqualTo("success");
    }
}