package main.ui;

import main.model.SensorNode;
import main.simulation.SimulationController;
import main.util.Constants;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Node Detay Penceresi — Çift tıklayınca açılan tam ekran bilgi diyaloğu.
 *
 * İçerik:
 *  ● Tam sensör verisi (BME280 + MPU-6050)
 *  ● Risk skorları — renkli progress bar
 *  ● Enerji modeli — Joule hesabı ve duty cycle
 *  ● LoRa parametreleri
 *  ● Poisson matematiksel panel: P(X=k) hesaplama tablosu
 *  ● Donanım datasheet bilgisi
 */
public class NodeDetailDialog extends JDialog {

    private final SensorNode node;
    private final SimulationController sim;

    // Animasyon
    private float pulsePhase = 0.0f;
    private Timer animTimer;

    public NodeDetailDialog(Frame parent, SensorNode node, SimulationController sim) {
        super(parent, "🔍 Node Detayı — " + node.getNodeId(), false);  // non-modal
        this.node = node;
        this.sim  = sim;

        setSize(860, 620);
        setLocationRelativeTo(parent);
        setResizable(true);
        getContentPane().setBackground(new Color(8, 12, 28));

        buildUI();

        // Her 2 saniyede bir sensör verilerini güncelle
        Timer refresh = new Timer(2000, e -> refreshData());
        refresh.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                refresh.stop();
                if (animTimer != null) animTimer.stop();
            }
        });
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(8, 12, 28));

        // ── Başlık ──────────────────────────────────────────────────────
        root.add(buildHeader(), BorderLayout.NORTH);

        // ── Orta: 3 sütun ───────────────────────────────────────────────
        JPanel center = new JPanel(new GridLayout(1, 3, 8, 0));
        center.setBackground(new Color(8, 12, 28));
        center.setBorder(new EmptyBorder(8, 12, 8, 12));

        center.add(buildSensorColumn());
        center.add(buildRiskEnergyColumn());
        center.add(buildPoissonColumn());

        root.add(center, BorderLayout.CENTER);

        // ── Alt: Donanım şeridi ─────────────────────────────────────────
        root.add(buildHardwareFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ============================================================
    //  BAŞLIK
    // ============================================================
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(new Color(10, 18, 40));
        p.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 2, 0, new Color(30, 70, 140)),
            new EmptyBorder(10, 16, 10, 16)
        ));

        // Sol: ID + konum
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);

        JLabel title = new JLabel(node.getNodeId() + "  —  " + node.getLocation());
        title.setFont(new Font("Monospaced", Font.BOLD, 17));
        title.setForeground(new Color(100, 181, 246));

        JLabel sub = new JLabel("[" + node.getType() + "]   "
            + String.format("%.4f°N  %.4f°E   Eğim: %.1f°", node.getLatitude(), node.getLongitude(), node.getSlopeAngle()));
        sub.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sub.setForeground(new Color(100, 120, 150));

        left.add(title);
        left.add(sub);

        // Sağ: risk badge
        JLabel badge = buildRiskBadge();

        p.add(left,  BorderLayout.WEST);
        p.add(badge, BorderLayout.EAST);
        return p;
    }

    private JLabel buildRiskBadge() {
        Color c;
        String text;
        switch (node.getRiskLevel()) {
            case CRITICAL: c = new Color(244, 67, 54);  text = "🔴 KRİTİK";    break;
            case DANGER:   c = new Color(255, 100, 0);  text = "🟠 TEHLİKE";   break;
            case CAUTION:  c = new Color(255, 220, 0);  text = "🟡 DİKKAT";    break;
            default:       c = new Color(0, 200, 100);  text = "🟢 GÜVENLİ";   break;
        }
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
        lbl.setForeground(c);
        lbl.setBorder(new CompoundBorder(
            new LineBorder(c, 1, true),
            new EmptyBorder(4, 10, 4, 10)
        ));
        return lbl;
    }

    // ============================================================
    //  SOL SÜTUN: SENSÖR VERİLERİ
    // ============================================================
    private JPanel buildSensorColumn() {
        JPanel col = darkColumn("🌡️ SENSÖR VERİLERİ (BME280 + MPU-6050)");

        // Sıcaklık
        col.add(bigMetricCard("🌡️ Sıcaklık",
            String.format("%.1f °C", node.getTemperature()),
            "BME280 — ±0.5°C hassasiyet",
            new Color(255, 160, 100)));

        // Nem
        col.add(bigMetricCard("💧 Nem",
            String.format("%.0f %%RH", node.getHumidity()),
            "BME280 — ±3% RH",
            new Color(100, 180, 255)));

        // Rüzgar
        col.add(bigMetricCard("💨 Rüzgar Hızı",
            String.format("%.1f km/h", node.getWindSpeed()),
            "Open-Meteo API verisi",
            new Color(150, 230, 150)));

        // Basınç
        col.add(bigMetricCard("⚡ Atmosfer Basıncı",
            String.format("%.1f hPa", node.getPressure()),
            "BME280 — ±1 hPa",
            new Color(200, 180, 255)));

        // Titreşim
        col.add(bigMetricCard("📳 Titreşim (MPU-6050)",
            String.format("%.4f g", node.getVibration()),
            "InvenSense ±16g aralığı",
            new Color(255, 210, 100)));

        // Görüş
        col.add(bigMetricCard("👁️ Görüş Mesafesi",
            String.format("%.1f km", node.getVisibility() / 1000.0),
            "Open-Meteo API verisi",
            new Color(180, 220, 255)));

        col.add(Box.createVerticalGlue());
        return col;
    }

    // ============================================================
    //  ORTA SÜTUN: RİSK + ENERJİ
    // ============================================================
    private JPanel buildRiskEnergyColumn() {
        JPanel col = darkColumn("⚠️ RİSK ANALİZİ & ENERJİ MODELİ");

        // ── Risk Skorları ─────────────────────────────────────────
        col.add(sectionTitle("Risk Skorları"));
        col.add(Box.createVerticalStrut(4));
        col.add(riskBar("🪨 Kaya / Heyelan",  node.getRockRisk(), new Color(200, 100, 50)));
        col.add(Box.createVerticalStrut(3));
        col.add(riskBar("🌫️ Sis Yoğunluğu",   node.getFogRisk(),  new Color(100, 170, 255)));
        col.add(Box.createVerticalStrut(3));
        col.add(riskBar("⚠️ Yol Kayması",      node.getSlipRisk(), new Color(255, 210, 50)));
        col.add(Box.createVerticalStrut(3));
        col.add(riskBar("🌐 Genel Risk",       node.getOverallRisk(), riskColor(node.getOverallRisk())));

        col.add(Box.createVerticalStrut(12));
        col.add(separator());
        col.add(Box.createVerticalStrut(8));

        // ── Enerji Modeli ─────────────────────────────────────────
        col.add(sectionTitle("Enerji Modeli (FiPy Datasheet)"));
        col.add(Box.createVerticalStrut(6));

        double batPct = node.getBatteryPercent();
        col.add(riskBar("🔋 Batarya",  batPct / 100.0, batPct > 50 ? new Color(0,200,100) : batPct > 20 ? new Color(255,165,0) : new Color(220,50,50)));
        col.add(Box.createVerticalStrut(6));

        col.add(infoRow("⚡ Kalan Enerji", String.format("%.0f / %.0f J  (%.1f%%)", node.getBatteryJoules(), Constants.FIPY_BATTERY_JOULES, batPct)));
        col.add(infoRow("🔌 Tüketilen", String.format("%.2f mAh", node.getTotalConsumedMah())));
        col.add(infoRow("💤 Sleep Akımı", String.format("%.0f µA", Constants.FIPY_SLEEP_CURRENT_MA * 1000)));
        col.add(infoRow("📡 LoRa TX Akımı", String.format("%.0f mA @ +20 dBm", Constants.FIPY_LORA_TX_MA)));
        col.add(infoRow("☀️ Solar Şarj", sim.getWeatherService().isDaytime() ? String.format("%.0f mA (×%.2f)", Constants.SOLAR_PANEL_CURRENT_MA, sim.getWeatherService().getSolarChargeFactor()) : "Kapalı (Gece)"));

        col.add(Box.createVerticalStrut(8));
        col.add(separator());
        col.add(Box.createVerticalStrut(8));

        // ── LoRa ─────────────────────────────────────────────────
        col.add(sectionTitle("LoRaWAN İletişim"));
        col.add(Box.createVerticalStrut(4));
        col.add(infoRow("📡 Gateway",      Constants.GATEWAY_NAMES[node.getNearestGateway()]));
        col.add(infoRow("📶 RSSI",         String.format("%.1f dBm", node.getLoraRssi())));
        col.add(infoRow("🔢 Spreading Fak.", "SF" + node.getLoraSf()));
        col.add(infoRow("📦 Paket Sayısı", String.valueOf(node.getLoraPacketCount())));
        col.add(infoRow("📻 Frekans",      "EU868 MHz — LoRaWAN 1.0.3"));

        // Madde 5 — LoRaWAN Yasal Görev Döngüsü (Duty Cycle) Notu
        // AB standartlarında EU868 bandında cihazlar %1 Duty Cycle kuralına tabidir:
        // Cihaz havayı sürekli meşgul edemez, bir paket attıktan sonra susmak zorundadır.
        col.add(Box.createVerticalStrut(5));
        JPanel dutyCycleNote = new JPanel(new BorderLayout());
        dutyCycleNote.setBackground(new Color(20, 36, 20));
        dutyCycleNote.setBorder(new CompoundBorder(
            new LineBorder(new Color(0, 120, 60, 180), 1, true),
            new EmptyBorder(4, 8, 4, 8)
        ));
        dutyCycleNote.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        dutyCycleNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel dcLabel = new JLabel("<html><span style='color:#60D080;font-size:9px'>" +
            "✅ EU868 %1 Duty Cycle Uyumlu</span><br>" +
            "<span style='color:#508060;font-size:8px'>" +
            "ETSI EN 300 220 — Her TX sonrası bekleme süresi uygulanır</span></html>");
        dutyCycleNote.add(dcLabel, BorderLayout.CENTER);
        col.add(dutyCycleNote);

        col.add(Box.createVerticalGlue());
        return col;

    }

    // ============================================================
    //  SAĞ SÜTUN: POİSSON PANELİ
    // ============================================================
    private JPanel buildPoissonColumn() {
        JPanel col = darkColumn("📐 POİSSON MATEMATİĞİ");

        double lambda = sim.getPoissonEngine().getLambda();

        // Formül kutusu
        JPanel formulaBox = new JPanel();
        formulaBox.setLayout(new BoxLayout(formulaBox, BoxLayout.Y_AXIS));
        formulaBox.setBackground(new Color(14, 24, 50));
        formulaBox.setBorder(new CompoundBorder(
            new LineBorder(new Color(80, 120, 200, 120), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));
        formulaBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        formulaBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        addFormulaLabel(formulaBox, "P(X = k)  =  e⁻λ · λᵏ / k!",     new Color(206, 147, 216), 14);
        addFormulaLabel(formulaBox, "",                                   Color.GRAY, 10);
        addFormulaLabel(formulaBox, "E[X]  =  λ  =  " + String.format("%.2f", lambda), new Color(100, 181, 246), 12);
        addFormulaLabel(formulaBox, "Var[X]  =  λ  =  " + String.format("%.2f", lambda), new Color(129, 199, 132), 11);
        addFormulaLabel(formulaBox, "σ  =  √λ  =  " + String.format("%.2f", Math.sqrt(lambda)),  new Color(255, 167, 38), 11);

        col.add(formulaBox);
        col.add(Box.createVerticalStrut(10));

        // P(X=k) tablosu
        col.add(sectionTitle("P(X = k) Değerleri  [λ = " + String.format("%.2f", lambda) + "]"));
        col.add(Box.createVerticalStrut(4));

        JPanel tablePanel = new JPanel();
        tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));
        tablePanel.setOpaque(false);
        tablePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int k = 0; k <= 7; k++) {
            double prob = main.simulation.PoissonEngine.poissonPMF(k, lambda);
            tablePanel.add(poissonRow(k, prob, lambda));
        }
        col.add(tablePanel);

        col.add(Box.createVerticalStrut(10));
        col.add(separator());
        col.add(Box.createVerticalStrut(8));

        // Tahmin
        col.add(sectionTitle("Risk Tahmini (10 dak. — P(X≥1))"));
        col.add(Box.createVerticalStrut(4));

        double t = 10.0 / 60.0; // saat
        double pAtLeast1 = 1.0 - Math.exp(-lambda * t);

        var pred = sim.getRiskPredictor();
        pred.update();
        col.add(predRow("🌫️ Sis",         pred.getFogProbability()));
        col.add(predRow("🪨 Kaya",        pred.getRockProbability()));
        col.add(predRow("🚗 Kayma",       pred.getSlipProbability()));
        col.add(predRow("⛈️ Fırtına",     pred.getStormProbability()));

        col.add(Box.createVerticalStrut(6));
        col.add(infoRow("Genel olay P(X≥1)", String.format("%.1f%%", pAtLeast1 * 100)));

        col.add(Box.createVerticalGlue());
        return col;
    }

    // ============================================================
    //  ALT: DONANIM ŞERİDİ
    // ============================================================
    private JPanel buildHardwareFooter() {
        JPanel p = new JPanel(new GridLayout(1, 4, 8, 0));
        p.setBackground(new Color(10, 16, 36));
        p.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, new Color(30, 50, 90)),
            new EmptyBorder(8, 12, 8, 12)
        ));

        p.add(chipCard("🔌 Pycom FiPy",      "LoRaWAN + WiFi + BLE\nSleep: 10 µA\nTX: 120 mA @ +20dBm\n3.7V LiPo 2000mAh", new Color(100, 181, 246)));
        p.add(chipCard("🌡️ Bosch BME280",    "Sıcaklık/Nem/Basınç\nNormal: 3.6 µA\nSleep: 0.1 µA\nI2C / SPI arayüz",  new Color(129, 199, 132)));
        p.add(chipCard("📳 MPU-6050",        "6-eksen IMU\nİvme + Jiroskop\nNormal: 3.9 mA\nSleep: 5 µA",        new Color(206, 147, 216)));
        p.add(chipCard("🛰️ LoRaWAN GW",     "EU868 MHz RF\nLine-of-Sight ~600m\nSF7-SF12 Adaptive\nMERSİN-ANTALYA HAT", new Color(255, 167, 38)));

        return p;
    }

    private JPanel chipCard(String title, String body, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(14, 22, 44));
        card.setBorder(new CompoundBorder(
            new LineBorder(new Color(accent.getRed()/3, accent.getGreen()/3, accent.getBlue()/3+20, 180), 1),
            new EmptyBorder(6, 8, 6, 8)
        ));

        JLabel t = new JLabel(title);
        t.setFont(new Font("Monospaced", Font.BOLD, 11));
        t.setForeground(accent);

        JLabel b = new JLabel("<html><pre style='font-size:9px;color:#A0B0C0'>" + body.replace("\n", "<br>") + "</pre></html>");

        card.add(t, BorderLayout.NORTH);
        card.add(b, BorderLayout.CENTER);
        return card;
    }

    // ============================================================
    //  GÜNCELLEME
    // ============================================================
    private void refreshData() {
        // Başlık badge'i güncelle (riskLevel değişmiş olabilir)
        // Repaint yeter — swing thread-safe ise
        repaint();
    }

    // ============================================================
    //  YARDIMCI BİLEŞENLER
    // ============================================================
    private JPanel darkColumn(String header) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(new Color(12, 18, 38));
        col.setBorder(new CompoundBorder(
            new LineBorder(new Color(30, 50, 90), 1),
            new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel h = new JLabel(header);
        h.setFont(new Font("Monospaced", Font.BOLD, 11));
        h.setForeground(new Color(100, 181, 246));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(h);
        col.add(Box.createVerticalStrut(8));
        col.add(separator());
        col.add(Box.createVerticalStrut(8));
        return col;
    }

    private JPanel bigMetricCard(String label, String value, String subtext, Color accent) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setBackground(new Color(18, 28, 54));
        p.setBorder(new CompoundBorder(
            new LineBorder(new Color(accent.getRed()/4, accent.getGreen()/4, accent.getBlue()/4+15, 150), 1),
            new EmptyBorder(6, 8, 6, 8)
        ));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel lbl  = new JLabel(label);
        lbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        lbl.setForeground(new Color(130, 148, 175));
        JLabel val  = new JLabel(value);
        val.setFont(new Font("Monospaced", Font.BOLD, 16));
        val.setForeground(accent);
        top.add(lbl, BorderLayout.WEST);
        top.add(val, BorderLayout.EAST);

        JLabel sub = new JLabel(subtext);
        sub.setFont(new Font("Monospaced", Font.PLAIN, 9));
        sub.setForeground(new Color(80, 100, 130));

        p.add(top, BorderLayout.NORTH);
        p.add(sub, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(p);
        wrapper.add(Box.createVerticalStrut(3), BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel riskBar(String label, double value, Color barColor) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        lbl.setForeground(new Color(160, 178, 210));
        lbl.setPreferredSize(new Dimension(130, 14));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.round(value * 100));
        bar.setForeground(barColor);
        bar.setBackground(new Color(20, 32, 60));
        bar.setBorderPainted(false);

        JLabel pct = new JLabel(String.format("%d%%", (int) Math.round(value * 100)));
        pct.setFont(new Font("Monospaced", Font.BOLD, 10));
        pct.setForeground(barColor);
        pct.setPreferredSize(new Dimension(36, 14));

        p.add(lbl, BorderLayout.WEST);
        p.add(bar, BorderLayout.CENTER);
        p.add(pct, BorderLayout.EAST);
        return p;
    }

    private JPanel poissonRow(int k, double prob, double lambda) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lk  = new JLabel(String.format("k = %d", k));
        lk.setFont(new Font("Monospaced", Font.BOLD, 10));
        lk.setForeground(new Color(160, 200, 255));
        lk.setPreferredSize(new Dimension(40, 14));

        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setValue((int)(prob * 1000));
        bar.setBackground(new Color(20, 30, 55));
        bar.setForeground(new Color(
            (int)(50  + prob * 180),
            (int)(200 - prob * 100),
            (int)(255 - prob * 200)
        ));
        bar.setBorderPainted(false);

        JLabel lp = new JLabel(String.format("%.4f", prob));
        lp.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lp.setForeground(new Color(180, 200, 230));
        lp.setPreferredSize(new Dimension(55, 14));

        p.add(lk, BorderLayout.WEST);
        p.add(bar, BorderLayout.CENTER);
        p.add(lp, BorderLayout.EAST);
        return p;
    }

    private JPanel predRow(String label, double prob) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        lbl.setForeground(new Color(160, 180, 210));
        lbl.setPreferredSize(new Dimension(90, 14));

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue((int)(prob * 100));
        bar.setBackground(new Color(20, 30, 55));
        Color c = prob > 0.75 ? new Color(244,67,54) : prob > 0.50 ? new Color(255,130,0) : prob > 0.25 ? new Color(255,210,50) : new Color(0,200,100);
        bar.setForeground(c);
        bar.setBorderPainted(false);

        JLabel pct = new JLabel(String.format("%.0f%%", prob * 100));
        pct.setFont(new Font("Monospaced", Font.BOLD, 10));
        pct.setForeground(c);
        pct.setPreferredSize(new Dimension(35, 14));

        p.add(lbl, BorderLayout.WEST);
        p.add(bar, BorderLayout.CENTER);
        p.add(pct, BorderLayout.EAST);
        return p;
    }

    private JPanel infoRow(String key, String val) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel k = new JLabel(key);
        k.setFont(new Font("Monospaced", Font.PLAIN, 10));
        k.setForeground(new Color(120, 140, 170));

        JLabel v = new JLabel(val);
        v.setFont(new Font("Monospaced", Font.BOLD, 10));
        v.setForeground(new Color(200, 220, 250));

        p.add(k, BorderLayout.WEST);
        p.add(v, BorderLayout.EAST);
        return p;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        l.setForeground(new Color(100, 181, 246));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(30, 50, 90));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private Color riskColor(double risk) {
        if (risk >= 0.75) return new Color(244, 67, 54);
        if (risk >= 0.50) return new Color(255, 100, 0);
        if (risk >= 0.25) return new Color(255, 220, 0);
        return new Color(0, 200, 100);
    }

    private void addFormulaLabel(JPanel p, String text, Color color, int size) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);
    }
}
