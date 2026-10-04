package com.agil.energy.service;

import com.agil.energy.dto.response.MonthlyReportData;

public interface ReportService {

    MonthlyReportData buildMonthlyReportData(Long stationId, int year, int month, String generatedBy);

    byte[] generateMonthlyPdf(Long stationId, int year, int month, String generatedBy);

    byte[] generateMonthlyExcel(Long stationId, int year, int month, String generatedBy);
}