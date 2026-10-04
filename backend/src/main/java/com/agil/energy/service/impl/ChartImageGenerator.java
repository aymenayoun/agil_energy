package com.agil.energy.service.impl;

import com.agil.energy.dto.response.MonthlyReportData;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Component
public class ChartImageGenerator {

    /** Daily sales line chart, one line per fuel type. */
    public byte[] generateDailySalesLineChart(
            List<MonthlyReportData.DailySeries> seriesList, int width, int height) {

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MonthlyReportData.DailySeries s : seriesList) {
            for (MonthlyReportData.DailyPoint p : s.getPoints()) {
                dataset.addValue(p.getValue(), s.getFuelTypeName(),
                        String.valueOf(p.getDate().getDayOfMonth()));
            }
        }
        JFreeChart chart = ChartFactory.createLineChart(
                "Ventes journalières (litres)", "Jour du mois", "Litres",
                dataset, PlotOrientation.VERTICAL, true, true, false);
        styleLineChart(chart);
        return toPng(chart, width, height);
    }

    /** Comparative bar chart: total sales vs total deliveries per fuel type. */
    public byte[] generateSalesVsDeliveriesBarChart(
            List<MonthlyReportData.FuelStat> fuelStats, int width, int height) {

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MonthlyReportData.FuelStat f : fuelStats) {
            dataset.addValue(f.getTotalSales(), "Ventes", f.getFuelTypeName());
            dataset.addValue(f.getTotalDeliveries(), "Livraisons", f.getFuelTypeName());
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "Ventes vs Livraisons (litres)", "Carburant", "Litres",
                dataset, PlotOrientation.VERTICAL, true, true, false);
        styleBarChart(chart);
        return toPng(chart, width, height);
    }

    /** Tank stock-level bar chart (% filled, colored if critical). */
    public byte[] generateTankStockChart(
            List<MonthlyReportData.TankSnapshot> tanks, int width, int height) {

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (MonthlyReportData.TankSnapshot t : tanks) {
            dataset.addValue(t.getStockPercentage(),
                    t.isCritical() ? "Critique" : "OK", t.getFuelTypeName());
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "Niveau de stock par réservoir (%)", "Carburant", "% rempli",
                dataset, PlotOrientation.VERTICAL, true, true, false);
        styleBarChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setRange(0, 100);
        return toPng(chart, width, height);
    }

    // ===================== helpers =====================

    private void styleLineChart(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(248, 249, 250));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        plot.setOutlineVisible(false);
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setDefaultStroke(new BasicStroke(2.2f));
        r.setDefaultShapesVisible(true);
    }

    private void styleBarChart(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(248, 249, 250));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        plot.setOutlineVisible(false);
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setShadowVisible(false);
        r.setBarPainter(new org.jfree.chart.renderer.category.StandardBarPainter());
        r.setSeriesPaint(0, new Color(39, 174, 96));   // green
        r.setSeriesPaint(1, new Color(47, 128, 237));  // blue
    }

    private byte[] toPng(JFreeChart chart, int width, int height) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ChartUtils.writeChartAsPNG(baos, chart, width, height);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la génération du graphique", e);
        }
        return baos.toByteArray();
    }

    /** Convert raw PNG bytes to a `data:image/png;base64,…` URI for inline HTML embedding. */
    public String toBase64DataUri(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}