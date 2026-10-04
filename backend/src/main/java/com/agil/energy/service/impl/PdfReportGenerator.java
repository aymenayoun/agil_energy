package com.agil.energy.service.impl;

import com.agil.energy.dto.response.MonthlyReportData;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class PdfReportGenerator {

    private final TemplateEngine templateEngine;
    private final ChartImageGenerator chartGen;

    public byte[] generate(MonthlyReportData data) throws IOException {
        // 1. Build chart images (base64) and inject into the template context
        String salesChart = chartGen.toBase64DataUri(
                chartGen.generateDailySalesLineChart(data.getDailySalesByFuel(), 900, 360));
        String comparisonChart = chartGen.toBase64DataUri(
                chartGen.generateSalesVsDeliveriesBarChart(data.getFuelStats(), 900, 360));
        String tankChart = chartGen.toBase64DataUri(
                chartGen.generateTankStockChart(data.getTanks(), 900, 360));

        Context ctx = new Context();
        ctx.setVariable("data", data);
        ctx.setVariable("salesChart", salesChart);
        ctx.setVariable("comparisonChart", comparisonChart);
        ctx.setVariable("tankChart", tankChart);

        // 2. Render Thymeleaf HTML
        String html = templateEngine.process("monthly-report", ctx);

        // 3. Convert HTML → PDF (openhtmltopdf, fast/lenient mode)
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        }
    }
}