package com.agil.energy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class IAClientService {

    private final RestTemplate restTemplate;
    private final RestTemplate longRunningRestTemplate;

    public IAClientService(
            RestTemplate restTemplate,
            @Qualifier("longRunningRestTemplate") RestTemplate longRunningRestTemplate) {
        this.restTemplate = restTemplate;
        this.longRunningRestTemplate = longRunningRestTemplate;
    }

    @Value("${ia.service.url:http://localhost:5000}")
    private String iaServiceUrl;
    @Value("${ia.service.api-key}")
    private String iaApiKey;
    /**
     * Appeler le microservice IA pour générer les prévisions d'une station/carburant.
     * Retourne la réponse brute du microservice.
     */
    public Map<String, Object> generatePrediction(Long stationId, Long fuelTypeId, int forecastDays) {
        String url = iaServiceUrl + "/ml/predict";

        Map<String, Object> body = new HashMap<>();
        body.put("station_id", stationId);
        body.put("fuel_type_id", fuelTypeId);
        body.put("forecast_days", forecastDays);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", iaApiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Appel IA microservice: station={}, fuel={}", stationId, fuelTypeId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Prédiction reçue avec succès pour station={}, fuel={}", stationId, fuelTypeId);
                return response.getBody();
            } else {
                log.error("Réponse IA non valide: {}", response.getStatusCode());
                return Map.of("status", "error", "message", "Réponse non valide du microservice IA");
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'appel au microservice IA: {}", e.getMessage());
            return Map.of("status", "error", "message", "Le microservice IA est indisponible: " + e.getMessage());
        }
    }

    /**
     * Lancer les prédictions batch pour toutes les stations.
     */
    public Map<String, Object> generateBatchPredictions(int forecastDays) {
        String url = iaServiceUrl + "/ml/predict/batch";

        Map<String, Object> body = new HashMap<>();
        body.put("forecast_days", forecastDays);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", iaApiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Lancement prédictions batch");
            ResponseEntity<Map> response = longRunningRestTemplate.postForEntity(url, request, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("status", "error");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Read timeout, connection refused, etc.
            log.error("Batch IA timeout/unreachable: {}", e.getMessage());
            throw new com.agil.energy.exception.BusinessException(
                    "Le microservice IA met trop de temps à répondre. "
                            + "Les modèles sont probablement en cours d'entraînement en arrière-plan. "
                            + "Réessayez dans quelques minutes.");
        } catch (Exception e) {
            log.error("Erreur batch IA: {}", e.getMessage());
            throw new com.agil.energy.exception.BusinessException(
                    "Échec du batch IA : " + e.getMessage());
        }
    }

    /**
     * Vérifier la santé du microservice IA.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(iaServiceUrl + "/health", Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
    public String buildMetricsUrl(Long stationId, Long fuelTypeId) {
        StringBuilder url = new StringBuilder(iaServiceUrl + "/ml/model-metrics?limit=50");
        if (stationId != null)   url.append("&station_id=").append(stationId);
        if (fuelTypeId != null)  url.append("&fuel_type_id=").append(fuelTypeId);
        return url.toString();
    }

    public String getIaApiKey() {
        return iaApiKey;
    }

    public Map<String, Object> getModelMetrics(Long stationId, Long fuelTypeId) {
        String url = buildMetricsUrl(stationId, fuelTypeId);
        log.info("Appel métriques IA: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", iaApiKey);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);
            log.info("Réponse métriques IA: status={}", response.getStatusCode());
            return response.getBody() != null ? response.getBody()
                    : Map.of("total", 0, "metrics", List.of());
        } catch (Exception e) {
            log.error("Erreur récupération métriques IA: {}", e.getMessage());
            return Map.of("total", 0, "metrics", List.of(), "error", e.getMessage());
        }
    }

    /**
     * Appeler le microservice IA pour une prédiction agrégée par région.
     */
    public Map<String, Object> generateRegionPrediction(String region, Long fuelTypeId, int forecastDays) {
        String url = iaServiceUrl + "/ml/predict/region";

        Map<String, Object> body = new HashMap<>();
        body.put("region", region);
        body.put("fuel_type_id", fuelTypeId);
        body.put("forecast_days", forecastDays);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", iaApiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Appel IA microservice (région): region={}, fuel={}", region, fuelTypeId);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Prédiction régionale reçue avec succès pour region={}, fuel={}", region, fuelTypeId);
                return response.getBody();
            } else {
                log.error("Réponse IA non valide (région): {}", response.getStatusCode());
                return Map.of("status", "error", "message", "Réponse non valide du microservice IA");
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'appel IA (région): {}", e.getMessage());
            return Map.of("status", "error", "message", "Le microservice IA est indisponible: " + e.getMessage());
        }
    }

    /**
     * Récupérer la liste des régions depuis le microservice IA.
     */
    public List<Map<String, Object>> getRegions() {
        String url = iaServiceUrl + "/ml/regions";
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur récupération régions: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Récupérer les types de carburant disponibles dans une région.
     */
    public List<Map<String, Object>> getRegionFuelTypes(String region) {
        String url = iaServiceUrl + "/ml/regions/" + region + "/fuel-types";
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Erreur récupération fuel types pour région {}: {}", region, e.getMessage());
            return List.of();
        }
    }

}