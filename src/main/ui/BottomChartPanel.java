package main.ui;

import main.simulation.PoissonEngine;
import main.simulation.SimulationController;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;

/**
 * Alt Grafik Paneli - JFreeChart ile canlı grafikler
 * 
 * 1. Poisson Dağılım Grafiği: P(X=k) değerleri
 * 2. Olay Sayısı Trend Grafiği: Zaman içinde olay sayısı
 * 3. Batarya Seviyesi Grafiği: En kritik 5 node'un batarya durumu
 * 4. Risk Trend Grafiği: Ortalama risk seviyesi
 */
public class BottomChartPanel extends JPanel {

    private final SimulationController sim;

    // Poisson dağılım grafiği
    private DefaultCategoryDataset poissonDataset;
    private JFreeChart poissonChart;

    // Event count trend
    private XYSeries eventCountSeries;
    private XYSeriesCollection eventDataset;

    // Risk trend
    private XYSeries rockRiskSeries, fogRiskSeries, slipRiskSeries;

    // Batarya
    private DefaultCategoryDataset batteryDataset;

    private long tickCounter = 0;

    public BottomChartPanel(SimulationController sim) {
        this.sim = sim;
        setLayout(new GridLayout(1, 4, 4, 0));
        setBackground(new Color(10, 15, 30));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(0, 220));

        buildCharts();
    }

    private void buildCharts() {
        add(createPoissonChart());
        add(createEventTrendChart());
        add(createRiskTrendChart());
        add(createBatteryChart());
    }

    // ============================================================
    // 1. POİSSON DAĞILIM GRAFİĞİ
    // ============================================================
    private JPanel createPoissonChart() {
        poissonDataset = new DefaultCategoryDataset();
        // Başlangıç değerleri
        PoissonEngine engine = sim.getPoissonEngine();
        updatePoissonData(engine.getLambda());

        JFreeChart chart = ChartFactory.createBarChart(
            "Poisson Dağılımı P(X=k)",
            "k (Olay Sayısı)",
            "P(X=k)",
            poissonDataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );

        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();

        // Bar rengi - gradient mor
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new GradientPaint(0, 0,
            new Color(100, 50, 200), 0, 100, new Color(200, 100, 255)));
        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);

        plot.setBackgroundPaint(new Color(12, 15, 28));
        plot.setRangeGridlinePaint(new Color(40, 50, 70));
        plot.getDomainAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getDomainAxis().setTickLabelPaint(new Color(140, 140, 160));
        plot.getRangeAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getRangeAxis().setTickLabelPaint(new Color(140, 140, 160));

        poissonChart = chart;
        ChartPanel cp = new ChartPanel(chart);
        cp.setBackground(new Color(10, 15, 30));

        return wrapChart(cp, "📊 Poisson Dağılımı");
    }

    // ============================================================
    // 2. OLAY SAYISI TREND GRAFİĞİ
    // ============================================================
    private JPanel createEventTrendChart() {
        eventCountSeries = new XYSeries("Olay Sayısı");
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(eventCountSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
            "Kümülatif Olay Sayısı",
            "Zaman (tick)",
            "Toplam Olay",
            dataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );

        styleChart(chart);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(12, 15, 28));
        plot.setRangeGridlinePaint(new Color(40, 50, 70));
        plot.getDomainAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getDomainAxis().setTickLabelPaint(new Color(140, 140, 160));
        plot.getRangeAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getRangeAxis().setTickLabelPaint(new Color(140, 140, 160));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(255, 193, 7));
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesShapesVisible(0, false);
        plot.setRenderer(renderer);

        ChartPanel cp = new ChartPanel(chart);
        cp.setBackground(new Color(10, 15, 30));
        return wrapChart(cp, "📈 Olay Trendi");
    }

    // ============================================================
    // 3. RİSK TREND GRAFİĞİ
    // ============================================================
    private JPanel createRiskTrendChart() {
        rockRiskSeries = new XYSeries("Kaya");
        fogRiskSeries  = new XYSeries("Sis");
        slipRiskSeries = new XYSeries("Kaygan");

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rockRiskSeries);
        dataset.addSeries(fogRiskSeries);
        dataset.addSeries(slipRiskSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
            "Risk Trendi",
            "Zaman (tick)",
            "Risk (0-1)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );

        styleChart(chart);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(new Color(12, 15, 28));
        plot.setRangeGridlinePaint(new Color(40, 50, 70));
        plot.getDomainAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getDomainAxis().setTickLabelPaint(new Color(140, 140, 160));
        plot.getRangeAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getRangeAxis().setTickLabelPaint(new Color(140, 140, 160));
        ((NumberAxis) plot.getRangeAxis()).setRange(0, 1.05);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(255, 107, 107));    // Kaya - kırmızı
        renderer.setSeriesPaint(1, new Color(100, 181, 246));    // Sis - mavi
        renderer.setSeriesPaint(2, new Color(255, 235, 59));     // Kaygan - sarı
        renderer.setSeriesStroke(0, new BasicStroke(1.8f));
        renderer.setSeriesStroke(1, new BasicStroke(1.8f));
        renderer.setSeriesStroke(2, new BasicStroke(1.8f));
        renderer.setSeriesShapesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesShapesVisible(2, false);
        plot.setRenderer(renderer);

        // Legend stilini düzenle
        chart.getLegend().setBackgroundPaint(new Color(16, 20, 35));
        chart.getLegend().setItemPaint(new Color(200, 200, 220));

        ChartPanel cp = new ChartPanel(chart);
        cp.setBackground(new Color(10, 15, 30));
        return wrapChart(cp, "⚠️ Risk Trendi");
    }

    // ============================================================
    // 4. BATARYA GRAFİĞİ
    // ============================================================
    private JPanel createBatteryChart() {
        batteryDataset = new DefaultCategoryDataset();

        JFreeChart chart = ChartFactory.createBarChart(
            "Node Batarya Durumu (%)",
            "Node",
            "Batarya %",
            batteryDataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );

        styleChart(chart);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(12, 15, 28));
        plot.setRangeGridlinePaint(new Color(40, 50, 70));
        plot.getDomainAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getDomainAxis().setTickLabelPaint(new Color(140, 140, 160));
        ((NumberAxis) plot.getRangeAxis()).setRange(0, 105);
        plot.getRangeAxis().setLabelPaint(new Color(180, 180, 200));
        plot.getRangeAxis().setTickLabelPaint(new Color(140, 140, 160));

        BatteryBarRenderer battRenderer = new BatteryBarRenderer();
        battRenderer.setDrawBarOutline(false);
        battRenderer.setShadowVisible(false);
        plot.setRenderer(battRenderer);

        ChartPanel cp = new ChartPanel(chart);
        cp.setBackground(new Color(10, 15, 30));
        return wrapChart(cp, "🔋 Batarya Durumu");
    }

    // ============================================================
    // VERİ GÜNCELLEME METODLARI
    // ============================================================

    /**
     * Poisson dağılım grafiğini güncelle
     */
    public void updatePoissonData(double lambda) {
        SwingUtilities.invokeLater(() -> {
            poissonDataset.clear();
            PoissonEngine tmp = new PoissonEngine(lambda);
            double[] dist = tmp.getPoissonDistribution(15);
            for (int k = 0; k <= 15; k++) {
                if (dist[k] > 0.0001) {
                    poissonDataset.addValue(dist[k], "P(X=k)", String.valueOf(k));
                }
            }
            // λ başlığı güncelle
            if (poissonChart != null) {
                poissonChart.setTitle(String.format("Poisson P(X=k) | λ=%.2f", lambda));
            }
        });
    }

    /**
     * Ana güncelleme tick'i
     */
    public void updateCharts(long tick, int totalEvents, List<main.model.SensorNode> nodes) {
        tickCounter = tick;

        SwingUtilities.invokeLater(() -> {
            // Event trend
            eventCountSeries.add(tick, totalEvents);
            if (eventCountSeries.getItemCount() > 100) {
                eventCountSeries.remove(0);
            }

            // Risk trend (aktif node'ların ortalaması)
            double avgRock = 0, avgFog = 0, avgSlip = 0;
            int activeCount = 0;
            int batteryUpdateCount = 0;

            for (main.model.SensorNode node : nodes) {
                if (!node.isActive()) continue;
                avgRock += node.getRockRisk();
                avgFog  += node.getFogRisk();
                avgSlip += node.getSlipRisk();
                activeCount++;
            }

            if (activeCount > 0) {
                rockRiskSeries.add(tick, avgRock / activeCount);
                fogRiskSeries.add(tick, avgFog / activeCount);
                slipRiskSeries.add(tick, avgSlip / activeCount);

                if (rockRiskSeries.getItemCount() > 80) rockRiskSeries.remove(0);
                if (fogRiskSeries.getItemCount() > 80) fogRiskSeries.remove(0);
                if (slipRiskSeries.getItemCount() > 80) slipRiskSeries.remove(0);
            }

            // Batarya: en kritik 10 node'u göster (en düşük batarya)
            batteryDataset.clear();
            nodes.stream()
                .filter(main.model.SensorNode::isActive)
                .sorted((a, b) -> Double.compare(a.getBatteryPercent(), b.getBatteryPercent()))
                .limit(10)
                .forEach(node -> {
                    batteryDataset.addValue(
                        node.getBatteryPercent(),
                        "Batarya",
                        node.getNodeId().replace("NODE-", "N")
                    );
                });
        });
    }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    private JPanel wrapChart(ChartPanel cp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(14, 18, 35));
        p.setBorder(new LineBorder(new Color(40, 55, 90), 1));

        JLabel lbl = new JLabel(title, SwingConstants.CENTER);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        lbl.setForeground(new Color(100, 181, 246));
        lbl.setBorder(new EmptyBorder(4, 0, 2, 0));
        lbl.setBackground(new Color(10, 14, 28));
        lbl.setOpaque(true);

        p.add(lbl, BorderLayout.NORTH);
        p.add(cp, BorderLayout.CENTER);
        return p;
    }

    private void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(new Color(14, 18, 35));
        chart.getTitle().setPaint(new Color(180, 200, 230));
        chart.getTitle().setFont(new Font("Monospaced", Font.BOLD, 11));
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(new Color(14, 18, 35));
            chart.getLegend().setItemPaint(new Color(200, 200, 220));
            chart.getLegend().setItemFont(new Font("Monospaced", Font.PLAIN, 10));
        }
    }

    /**
     * Batarya seviyesine göre renkli bar renderer
     */
    private static class BatteryBarRenderer extends BarRenderer {
        @Override
        public Paint getItemPaint(int row, int column) {
            if (batteryPct != null && column < batteryPct.length) {
                double pct = batteryPct[column];
                if (pct > 60) return new Color(76, 175, 80);       // Yeşil
                else if (pct > 30) return new Color(255, 152, 0);  // Turuncu
                else return new Color(244, 67, 54);                  // Kırmızı
            }
            return new Color(76, 175, 80);
        }

        private double[] batteryPct;
        public void setBatteryPct(double[] pct) { this.batteryPct = pct; }
    }
}
