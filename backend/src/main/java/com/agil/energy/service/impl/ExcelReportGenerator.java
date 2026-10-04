package com.agil.energy.service.impl;

import com.agil.energy.dto.response.MonthlyReportData;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ExcelReportGenerator {

    private final ChartImageGenerator chartGen;

    public byte[] generate(MonthlyReportData data) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = headerStyle(wb);
            CellStyle titleStyle  = titleStyle(wb);
            CellStyle moneyStyle  = numberStyle(wb);

            // ---- 1. Synthèse ----
            XSSFSheet summary = wb.createSheet("Synthèse");
            int r = 0;
            r = writeTitle(summary, r, "Rapport mensuel — " + data.getStationName(), titleStyle);
            r = writeKeyValue(summary, r, "Période",
                    data.getMonthName() + " " + data.getYear()
                            + "   (" + data.getStartDate() + " → " + data.getEndDate() + ")");
            r = writeKeyValue(summary, r, "Région", data.getStationRegion());
            r = writeKeyValue(summary, r, "Adresse", data.getStationAddress() == null ? "-" : data.getStationAddress());
            r = writeKeyValue(summary, r, "Statut station", data.getStationStatus());
            r = writeKeyValue(summary, r, "Généré le", String.valueOf(data.getGeneratedAt()));
            r = writeKeyValue(summary, r, "Généré par", data.getGeneratedBy());
            r++;
            r = writeKeyValue(summary, r, "Ventes totales (L)",  data.getTotalSales().toPlainString());
            r = writeKeyValue(summary, r, "Livraisons totales (L)", data.getTotalDeliveries().toPlainString());
            r = writeKeyValue(summary, r, "Alertes du mois", String.valueOf(data.getAlertsCreatedDuringPeriod()));
            r = writeKeyValue(summary, r, "Alertes actives",  String.valueOf(data.getActiveAlertsAtEndOfPeriod()));
            r = writeKeyValue(summary, r, "Nombre de réservoirs", String.valueOf(data.getTankCount()));
            summary.setColumnWidth(0, 7000);
            summary.setColumnWidth(1, 8000);

            // ---- 2. Synthèse par carburant ----
            XSSFSheet fuel = wb.createSheet("Par carburant");
            String[] fuelHeaders = {"Carburant", "Ventes (L)", "Livraisons (L)",
                    "Moy. journalière (L)", "Stock actuel (L)", "Capacité (L)", "% rempli"};
            writeRow(fuel, 0, fuelHeaders, headerStyle);
            int rowIdx = 1;
            for (MonthlyReportData.FuelStat f : data.getFuelStats()) {
                Row row = fuel.createRow(rowIdx++);
                row.createCell(0).setCellValue(f.getFuelTypeName());
                setNum(row, 1, f.getTotalSales().doubleValue(),       moneyStyle);
                setNum(row, 2, f.getTotalDeliveries().doubleValue(),  moneyStyle);
                setNum(row, 3, f.getAvgDailySales().doubleValue(),    moneyStyle);
                setNum(row, 4, f.getCurrentStock().doubleValue(),     moneyStyle);
                setNum(row, 5, f.getCapacity().doubleValue(),         moneyStyle);
                setNum(row, 6, f.getStockPercentage().doubleValue(),  moneyStyle);
            }
            autoSize(fuel, fuelHeaders.length);

            // Embedded comparison chart image
            byte[] cmpPng = chartGen.generateSalesVsDeliveriesBarChart(
                    data.getFuelStats(), 700, 320);
            embedImage(wb, fuel, cmpPng, rowIdx + 2, 0, 8, 18);

            // ---- 3. Réservoirs ----
            XSSFSheet tankSheet = wb.createSheet("Réservoirs");
            String[] tankHeaders = {"Carburant", "Capacité (L)", "Stock (L)",
                    "Seuil critique (L)", "% rempli", "État"};
            writeRow(tankSheet, 0, tankHeaders, headerStyle);
            rowIdx = 1;
            for (MonthlyReportData.TankSnapshot t : data.getTanks()) {
                Row row = tankSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(t.getFuelTypeName());
                setNum(row, 1, t.getCapacity().doubleValue(),          moneyStyle);
                setNum(row, 2, t.getCurrentStock().doubleValue(),      moneyStyle);
                setNum(row, 3, t.getCriticalThreshold().doubleValue(), moneyStyle);
                setNum(row, 4, t.getStockPercentage().doubleValue(),   moneyStyle);
                row.createCell(5).setCellValue(t.isCritical() ? "CRITIQUE" : "OK");
            }
            autoSize(tankSheet, tankHeaders.length);

            byte[] tankPng = chartGen.generateTankStockChart(data.getTanks(), 700, 320);
            embedImage(wb, tankSheet, tankPng, rowIdx + 2, 0, 8, 18);

            // ---- 4. Ventes journalières (avec graphique en image) ----
            XSSFSheet salesSheet = wb.createSheet("Ventes journalières");
            String[] salesHeaders = {"Date", "Carburant", "Quantité (L)", "Validée"};
            writeRow(salesSheet, 0, salesHeaders, headerStyle);
            rowIdx = 1;
            for (MonthlyReportData.SaleSummary s : data.getSales()) {
                Row row = salesSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(String.valueOf(s.getSaleDate()));
                row.createCell(1).setCellValue(s.getFuelTypeName());
                setNum(row, 2, s.getQuantity().doubleValue(), moneyStyle);
                row.createCell(3).setCellValue(s.isValidated() ? "Oui" : "Non");
            }
            autoSize(salesSheet, salesHeaders.length);

            byte[] salesPng = chartGen.generateDailySalesLineChart(
                    data.getDailySalesByFuel(), 800, 320);
            embedImage(wb, salesSheet, salesPng, rowIdx + 2, 0, 9, 18);

            // ---- 5. Livraisons ----
            XSSFSheet delivSheet = wb.createSheet("Livraisons");
            String[] delivHeaders = {"Date", "Carburant", "Quantité (L)", "Validée"};
            writeRow(delivSheet, 0, delivHeaders, headerStyle);
            rowIdx = 1;
            for (MonthlyReportData.DeliverySummary d : data.getDeliveries()) {
                Row row = delivSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(String.valueOf(d.getDeliveryDate()));
                row.createCell(1).setCellValue(d.getFuelTypeName());
                setNum(row, 2, d.getQuantity().doubleValue(), moneyStyle);
                row.createCell(3).setCellValue(d.isValidated() ? "Oui" : "Non");
            }
            autoSize(delivSheet, delivHeaders.length);

            // ---- 6. Alertes ----
            XSSFSheet alertSheet = wb.createSheet("Alertes");
            String[] alertHeaders = {"Date", "Type", "Sévérité", "Statut", "Message"};
            writeRow(alertSheet, 0, alertHeaders, headerStyle);
            rowIdx = 1;
            for (MonthlyReportData.AlertSummary a : data.getAlerts()) {
                Row row = alertSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(String.valueOf(a.getCreatedAt()));
                row.createCell(1).setCellValue(a.getAlertType());
                row.createCell(2).setCellValue(a.getSeverity());
                row.createCell(3).setCellValue(a.getStatus());
                row.createCell(4).setCellValue(a.getMessage());
            }
            autoSize(alertSheet, alertHeaders.length);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ===================== helpers =====================

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private int writeTitle(Sheet sheet, int rowIdx, String text, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 4));
        return rowIdx + 2;
    }

    private int writeKeyValue(Sheet sheet, int rowIdx, String key, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private void writeRow(Sheet sheet, int rowIdx, String[] values, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(values[i]);
            if (style != null) c.setCellStyle(style);
        }
    }

    private void setNum(Row row, int col, double v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v);
        c.setCellStyle(style);
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }

    private void embedImage(XSSFWorkbook wb, XSSFSheet sheet, byte[] png,
                            int row1, int col1, int colSpan, int rowSpan) {
        int pictureIdx = wb.addPicture(png, Workbook.PICTURE_TYPE_PNG);
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(col1);
        anchor.setRow1(row1);
        anchor.setCol2(col1 + colSpan);
        anchor.setRow2(row1 + rowSpan);
        drawing.createPicture(anchor, pictureIdx);
    }
}