package com.agil.energy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AIClientService {

    private final RestTemplate aiRestTemplate;

    public AIClientService(@Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.aiRestTemplate = aiRestTemplate;
    }

    @Value("${ia.service.url:http://localhost:5000}")
    private String iaServiceUrl;

    @Value("${ia.service.api-key}")
    private String iaApiKey;

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-API-Key", iaApiKey);
        return h;
    }

    public Map<String, Object> chat(String question, String mode) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("mode", mode);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
        try {
            ResponseEntity<Map> resp = aiRestTemplate.postForEntity(
                    iaServiceUrl + "/ai/chat", req, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of("error", "empty");
        } catch (Exception e) {
            log.error("AI chat error: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> explain(Long stationId, Long fuelTypeId, String mode) {
        Map<String, Object> body = new HashMap<>();
        body.put("station_id", stationId);
        body.put("fuel_type_id", fuelTypeId);
        body.put("mode", mode);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
        try {
            ResponseEntity<Map> resp = aiRestTemplate.postForEntity(
                    iaServiceUrl + "/ai/explain", req, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of("error", "empty");
        } catch (Exception e) {
            log.error("AI explain error: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> rebuildIndex() {
        HttpEntity<?> req = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<Map> resp = aiRestTemplate.postForEntity(
                    iaServiceUrl + "/ai/rag/rebuild", req, Map.class);
            return resp.getBody() != null ? resp.getBody() : Map.of("error", "empty");
        } catch (Exception e) {
            log.error("AI rebuild error: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}