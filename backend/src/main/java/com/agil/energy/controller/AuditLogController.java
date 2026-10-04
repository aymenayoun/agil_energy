package com.agil.energy.controller;

import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.entity.AuditLog;
import com.agil.energy.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("CREATE_SALE", "Création de vente"),
            Map.entry("VALIDATE_SALE", "Validation de vente"),
            Map.entry("CREATE_DELIVERY", "Création de livraison"),
            Map.entry("VALIDATE_DELIVERY", "Validation de livraison"),
            Map.entry("CREATE_STATION", "Création de station"),
            Map.entry("DEACTIVATE_STATION", "Désactivation de station"),
            Map.entry("ACTIVATE_STATION", "Activation de station"),
            Map.entry("CREATE_USER", "Création d'utilisateur"),
            Map.entry("DEACTIVATE_USER", "Désactivation d'utilisateur"),
            Map.entry("ACTIVATE_USER", "Activation d'utilisateur"),
            Map.entry("ADJUST_STOCK", "Ajustement de stock"),
            Map.entry("RESOLVE_ALERT", "Résolution d'alerte"),
            Map.entry("ADD_TANK", "Ajout de réservoir"),
            Map.entry("GENERATE_PREDICTION", "Génération de prévision IA")
    );

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuditLogs(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "100") int limit) {

        List<AuditLog> logs;

        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            logs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        } else {
            logs = auditLogRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(0, limit,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
            ).getContent();
        }

        List<Map<String, Object>> formatted = logs.stream().map(log -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", log.getId());
            entry.put("date", log.getCreatedAt() != null ?
                    log.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "");
            entry.put("utilisateur", log.getUser() != null ? log.getUser().getName() : "Système");
            entry.put("role", log.getUser() != null ? log.getUser().getRole().getName() : "SYSTEM");
            entry.put("action", log.getAction());
            entry.put("action_label", ACTION_LABELS.getOrDefault(log.getAction(), log.getAction()));
            entry.put("entite", log.getEntity());
            entry.put("entite_id", log.getEntityId());
            entry.put("details", log.getDetails());
            entry.put("ip", log.getIpAddress());
            return entry;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", formatted.size());
        response.put("logs", formatted);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        List<AuditLog> logs;

        if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            logs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        } else {
            logs = auditLogRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(0, 500,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
            ).getContent();
        }

        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM so Excel detects encoding and separator correctly
        csv.append("\uFEFF");
        csv.append("Date;Utilisateur;Role;Action;Entite;ID Entite;Details\n");

        for (AuditLog log : logs) {
            String date = log.getCreatedAt() != null ?
                    log.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "";
            String user = log.getUser() != null ? log.getUser().getName() : "Systeme";
            String role = log.getUser() != null ? log.getUser().getRole().getName() : "SYSTEM";
            String actionLabel = ACTION_LABELS.getOrDefault(log.getAction(), log.getAction());
            String entity = log.getEntity() != null ? log.getEntity() : "";
            String entityId = log.getEntityId() != null ? log.getEntityId().toString() : "";
            String details = formatDetailsForCsv(log.getDetails());

            csv.append(String.format("\"%s\";%s;%s;%s;%s;%s;%s\n",
                    date, user, role, actionLabel, entity, entityId, details));
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = "audit_log_agil_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /**
     * Formats JSON details into a human-readable string for CSV export.
     * e.g. {"format": "EXCEL", "period": "2026-04"} → "format: EXCEL | period: 2026-04"
     */
    private String formatDetailsForCsv(String details) {
        if (details == null || details.isBlank()) return "";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?, ?> obj = mapper.readValue(details, Map.class);
            String formatted = obj.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(" | "));
            // Remove semicolons to avoid breaking CSV columns
            return formatted.replace(";", ",");
        } catch (Exception e) {
            return details.replace(";", ",");
        }
    }
}