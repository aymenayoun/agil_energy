package com.agil.energy.controller;

import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.entity.AuditLog;
import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditLogController controller;

    private AuditLog log;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setName("ADMIN");
        User user = new User();
        user.setId(1L); user.setName("Admin"); user.setRole(role);

        log = new AuditLog();
        log.setId(1L);
        log.setAction("CREATE_SALE");
        log.setEntity("SALE");
        log.setEntityId(99L);
        log.setUser(user);
        log.setIpAddress("127.0.0.1");
        log.setDetails("test details");
        log.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 30));
    }

    @Test
    @DisplayName("getAuditLogs — no date filter uses pagination")
    void getAuditLogs_noFilter_returnsPagedResults() {
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getAuditLogs(null, null, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().get("total")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) response.getBody().getData().get("logs");
        assertThat(logs).hasSize(1);
        // CREATE_SALE should map to "Création de vente"
        assertThat(logs.get(0).get("action_label")).isEqualTo("Création de vente");
    }

    @Test
    @DisplayName("getAuditLogs — with date range queries by date")
    void getAuditLogs_withDates_queriesByDate() {
        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(log));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getAuditLogs("2026-05-01", "2026-05-31", 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().get("total")).isEqualTo(1);
    }

    @Test
    @DisplayName("getAuditLogs — null user maps to 'Système'")
    void getAuditLogs_nullUser_mapsToSystem() {
        log.setUser(null);
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getAuditLogs(null, null, 100);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) response.getBody().getData().get("logs");
        assertThat(logs.get(0).get("utilisateur")).isEqualTo("Système");
        assertThat(logs.get(0).get("role")).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("getAuditLogs — unknown action falls back to action code")
    void getAuditLogs_unknownAction_fallsBackToCode() {
        log.setAction("CUSTOM_ACTION");
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.getAuditLogs(null, null, 100);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) response.getBody().getData().get("logs");
        assertThat(logs.get(0).get("action_label")).isEqualTo("CUSTOM_ACTION");
    }

    @Test
    @DisplayName("exportCsv — no date range returns CSV with header")
    void exportCsv_noFilter_returnsCsv() {
        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<byte[]> response = controller.exportCsv(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        // CSV now starts with UTF-8 BOM (\uFEFF)
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Date;Utilisateur;Role");
        assertThat(csv).contains("Création de vente");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("audit_log_agil_");
    }

    @Test
    @DisplayName("exportCsv — with date range queries by date")
    void exportCsv_withDates_queriesByDate() {
        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(log));

        ResponseEntity<byte[]> response = controller.exportCsv("2026-05-01", "2026-05-31");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }
}