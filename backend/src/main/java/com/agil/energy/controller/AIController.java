package com.agil.energy.controller;

import com.agil.energy.dto.response.ApiResponse;
import com.agil.energy.service.AIClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AIController {

    private final AIClientService aiClientService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        String mode = (String) body.getOrDefault("mode", "default");
        Map<String, Object> result = aiClientService.chat(question, mode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/explain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> explain(@RequestBody Map<String, Object> body) {
        Long stationId = Long.valueOf(body.get("stationId").toString());
        Long fuelTypeId = Long.valueOf(body.get("fuelTypeId").toString());
        String mode = (String) body.getOrDefault("mode", "default");
        Map<String, Object> result = aiClientService.explain(stationId, fuelTypeId, mode);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/rag/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rebuild() {
        Map<String, Object> result = aiClientService.rebuildIndex();
        return ResponseEntity.ok(ApiResponse.success("Index reconstruit", result));
    }
}