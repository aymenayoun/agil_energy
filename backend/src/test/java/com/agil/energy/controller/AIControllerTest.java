package com.agil.energy.controller;

import com.agil.energy.service.AIClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIControllerTest {

    @Mock private AIClientService aiClientService;
    @InjectMocks private AIController controller;

    @Test
    @DisplayName("chat — returns 200 with result")
    void chat_returns200() {
        Map<String, Object> result = Map.of("answer", "test reply");
        when(aiClientService.chat("hello", "default")).thenReturn(result);

        var response = controller.chat(Map.of("question", "hello", "mode", "default"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).containsEntry("answer", "test reply");
    }

    @Test
    @DisplayName("chat — defaults mode to 'default'")
    void chat_defaultsMode() {
        when(aiClientService.chat("q", "default")).thenReturn(Map.of());

        controller.chat(Map.of("question", "q"));

        verify(aiClientService).chat("q", "default");
    }

    @Test
    @DisplayName("explain — passes stationId and fuelTypeId")
    void explain_passesIds() {
        when(aiClientService.explain(1L, 2L, "default")).thenReturn(Map.of("explanation", "x"));

        var response = controller.explain(Map.of("stationId", 1, "fuelTypeId", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiClientService).explain(1L, 2L, "default");
    }

    @Test
    @DisplayName("rebuild — returns 200")
    void rebuild_returns200() {
        when(aiClientService.rebuildIndex()).thenReturn(Map.of("status", "ok"));

        var response = controller.rebuild();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(aiClientService).rebuildIndex();
    }
}