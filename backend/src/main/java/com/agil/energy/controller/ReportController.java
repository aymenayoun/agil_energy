package com.agil.energy.controller;

import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class ReportController {

    private final ReportService reportService;
    private final AuditService auditService;

    /**
     * GET /api/reports/stations/{stationId}/monthly?year=2026&month=4&format=PDF
     * format = PDF (default) or EXCEL
     */
    @GetMapping("/stations/{stationId}/monthly")
    public ResponseEntity<byte[]> downloadMonthlyReport(
            @PathVariable Long stationId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false, defaultValue = "PDF") String format,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest httpRequest) {

        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        String fmt = format.toUpperCase();

        String generatedBy = currentUser != null ? currentUser.getName() : "Système";

        byte[] bytes;
        MediaType contentType;
        String fileExt;

        if ("EXCEL".equals(fmt) || "XLSX".equals(fmt)) {
            bytes = reportService.generateMonthlyExcel(stationId, y, m, generatedBy);
            contentType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            fileExt = "xlsx";
        } else {
            bytes = reportService.generateMonthlyPdf(stationId, y, m, generatedBy);
            contentType = MediaType.APPLICATION_PDF;
            fileExt = "pdf";
        }

        String filename = String.format("rapport_station_%d_%d_%02d.%s", stationId, y, m, fileExt);

        if (currentUser != null) {
            String details = String.format(
                    "{\"format\":\"%s\",\"period\":\"%d-%02d\"}", fmt, y, m);
            auditService.log(currentUser.getId(), "EXPORT", "REPORT", stationId,
                    details, httpRequest.getRemoteAddr());
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}