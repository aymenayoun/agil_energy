package com.agil.energy.controller;

import com.agil.energy.entity.Role;
import com.agil.energy.entity.User;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.security.CustomUserDetails;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private ReportService reportService;
    @Mock private AuditService auditService;
    @InjectMocks private ReportController controller;

    private CustomUserDetails currentUser;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setName("ADMIN");
        User user = new User();
        user.setId(1L); user.setName("Admin"); user.setEmail("admin@x.com");
        user.setStatus(UserStatus.ACTIVE); user.setRole(role);
        currentUser = new CustomUserDetails(user);
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
    }

    @Test
    @DisplayName("downloadMonthlyReport — PDF format returns PDF content type")
    void downloadMonthlyReport_pdf() {
        byte[] pdfBytes = "fake-pdf".getBytes();
        when(reportService.generateMonthlyPdf(1L, 2025, 4, "Admin")).thenReturn(pdfBytes);

        var response = controller.downloadMonthlyReport(1L, 2025, 4, "PDF", currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("rapport_station_1_2025_04.pdf");
        assertThat(response.getBody()).isEqualTo(pdfBytes);
        verify(auditService).log(eq(1L), eq("EXPORT"), eq("REPORT"), eq(1L), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("downloadMonthlyReport — EXCEL format returns xlsx content type")
    void downloadMonthlyReport_excel() {
        byte[] xlsxBytes = "fake-xlsx".getBytes();
        when(reportService.generateMonthlyExcel(1L, 2025, 3, "Admin")).thenReturn(xlsxBytes);

        var response = controller.downloadMonthlyReport(1L, 2025, 3, "EXCEL", currentUser, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("rapport_station_1_2025_03.xlsx");
    }
}