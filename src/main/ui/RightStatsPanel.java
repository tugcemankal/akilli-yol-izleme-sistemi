package main.ui;

import main.model.RiskEvent;
import main.model.SensorNode;
import main.simulation.SimulationController;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Sağ İstatistik ve Log Paneli
 * - Canlı hava durumu bilgisi
 * - LoRaWAN log akışı
 * - Canlı olay akışı
 * - CSV export butonu
 * - Aktif alarmlar
 */
public class RightStatsPanel extends JPanel {

    private final SimulationController sim;

    private JLabel lblTemp, lblHum, lblWind, lblPressure, lblPrecip, lblVis;
    private JLabel lblWeather;
    private DefaultListModel<String> loraLogModel;
    private JList<String> loraLogList;
    private DefaultListModel<String> eventLogModel;
    private JList<String> eventLogList;
    private JLabel lblTotalEvents, lblCritical, lblActive;
    private JButton btnExportNodes, btnExportEvents;
    private JLabel lblSolarStatus;

    // Prediction panel bileşenleri
    private JProgressBar barFog, barRock, barSlip, barStorm;
    private JLabel lblFogPct, lblRockPct, lblSlipPct, lblStormPct;
    private JLabel lblPredAdvice;
    private JLabel lblNodeSleep, lblNodeSense, lblNodeTx, lblNodeEmergency;

    public RightStatsPanel(SimulationController sim) {
        this.sim = sim;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(10, 15, 30));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(280, 0));

        buildUI();
    }

    private void buildUI() {
        // Hava Durumu Kartı
        add(createSectionLabel("🌡️ GERÇEK ZAMANLI HAVA"));
        add(Box.createVerticalStrut(6));
        add(createWeatherCard());
        add(Box.createVerticalStrut(12));

        // Enerji / Solar + Node Durum
        add(createSectionLabel("⚡ ENERJİ & NODE DURUMU"));
        add(Box.createVerticalStrut(6));
        add(createEnergyCard());
        add(Box.createVerticalStrut(12));

        // 🔮 Risk Tahmin Paneli
        add(createSectionLabel("🔮 RİSK TAHMİNİ (Önümüzdeki 10 dak.)"));
        add(Box.createVerticalStrut(6));
        add(createPredictionPanel());
        add(Box.createVerticalStrut(12));

        // LoRa Logları
        add(createSectionLabel("📡 LoRaWAN CANLI LOG"));
        add(Box.createVerticalStrut(6));
        add(createLoraLogPanel());
        add(Box.createVerticalStrut(12));

        // Olay Akışı
        add(createSectionLabel("🚨 CANLI OLAY AKIŞI"));
        add(Box.createVerticalStrut(6));
        add(createEventLogPanel());
        add(Box.createVerticalStrut(12));

        // CSV Export
        add(createSectionLabel("💾 RAPOR DIŞA AKTARMA"));
        add(Box.createVerticalStrut(6));
        add(createExportPanel());
    }

    // ============================================================
    // UI BİLEŞENLERİ
    // ============================================================

    private JPanel createWeatherCard() {
        JPanel p = createDarkCard();
        p.setLayout(new GridLayout(0, 2, 4, 4));

        lblWeather = new JLabel("🌍 Bağlanıyor...");
        lblWeather.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
        lblWeather.setForeground(new Color(255, 220, 100));

        lblTemp = new JLabel("--°C");
        lblHum  = new JLabel("--%");
        lblWind = new JLabel("-- km/h");
        lblPressure = new JLabel("-- hPa");
        lblPrecip   = new JLabel("-- mm/h");
        lblVis      = new JLabel("-- km");

        styleWeatherLabel(lblTemp, new Color(255, 160, 100));
        styleWeatherLabel(lblHum,  new Color(100, 180, 255));
        styleWeatherLabel(lblWind, new Color(150, 220, 150));
        styleWeatherLabel(lblPressure, new Color(200, 180, 255));
        styleWeatherLabel(lblPrecip,   new Color(100, 200, 255));
        styleWeatherLabel(lblVis,      new Color(200, 200, 200));

        p.add(new JPanel() {{setOpaque(false);}});
        p.add(lblWeather);
        p.add(darkLabel("🌡️ Sıcaklık:"));
        p.add(lblTemp);
        p.add(darkLabel("💧 Nem:"));
        p.add(lblHum);
        p.add(darkLabel("💨 Rüzgar:"));
        p.add(lblWind);
        p.add(darkLabel("⚡ Basınç:"));
        p.add(lblPressure);
        p.add(darkLabel("🌧️ Yağış:"));
        p.add(lblPrecip);
        p.add(darkLabel("👁️ Görüş:"));
        p.add(lblVis);

        return p;
    }

    private JPanel createEnergyCard() {
        JPanel p = createDarkCard();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        lblSolarStatus = new JLabel("☀️ Solar: Hesaplanıyor...", SwingConstants.CENTER);
        lblSolarStatus.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        lblSolarStatus.setForeground(new Color(255, 220, 50));
        lblSolarStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel statsRow = new JPanel(new GridLayout(1, 3, 4, 0));
        statsRow.setOpaque(false);

        JPanel activeBox   = createStatBoxPanel("Aktif",  "0",  new Color(76, 175, 80),  box -> lblActive = box);
        JPanel criticalBox = createStatBoxPanel("Kritik", "0",  new Color(244, 67, 54), box -> lblCritical = box);
        JPanel eventsBox   = createStatBoxPanel("Olay",   "0",  new Color(255, 193, 7),  box -> lblTotalEvents = box);

        statsRow.add(activeBox);
        statsRow.add(criticalBox);
        statsRow.add(eventsBox);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Node Durum Sayaçları (Sleep / Sense / TX / Emergency)
        JPanel stateRow = new JPanel(new GridLayout(2, 2, 4, 4));
        stateRow.setOpaque(false);
        stateRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        stateRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblNodeSleep     = createStateLabel("💤 Sleep: 0",    new Color(130, 150, 180));
        lblNodeSense     = createStateLabel("👁 Sense: 0",    new Color(100, 200, 150));
        lblNodeTx        = createStateLabel("📡 TX: 0",        new Color(100, 181, 246));
        lblNodeEmergency = createStateLabel("🚨 Alarm: 0",    new Color(244, 67, 54));

        stateRow.add(lblNodeSleep);
        stateRow.add(lblNodeSense);
        stateRow.add(lblNodeTx);
        stateRow.add(lblNodeEmergency);

        p.add(lblSolarStatus);
        p.add(Box.createVerticalStrut(6));
        p.add(statsRow);
        p.add(Box.createVerticalStrut(6));
        p.add(stateRow);
        return p;
    }

    /** Prediction (Risk Tahmin) Paneli */
    private JPanel createPredictionPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Tavsiye metni
        lblPredAdvice = new JLabel("Simülasyonu başlatın.");
        lblPredAdvice.setFont(new Font("Monospaced", Font.BOLD, 10));
        lblPredAdvice.setForeground(new Color(200, 230, 255));
        lblPredAdvice.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Risk çubukları
        barFog   = createPredBar(new Color(100, 180, 255));
        barRock  = createPredBar(new Color(200, 130, 60));
        barSlip  = createPredBar(new Color(255, 220, 50));
        barStorm = createPredBar(new Color(244, 67, 54));

        lblFogPct   = createPredPct();
        lblRockPct  = createPredPct();
        lblSlipPct  = createPredPct();
        lblStormPct = createPredPct();

        p.add(lblPredAdvice);
        p.add(Box.createVerticalStrut(8));
        p.add(buildPredRow("🌫️ Sis",        barFog,   lblFogPct));
        p.add(Box.createVerticalStrut(5));
        p.add(buildPredRow("🪨 Kaya/Heyelan", barRock,  lblRockPct));
        p.add(Box.createVerticalStrut(5));
        p.add(buildPredRow("🚗 Yol Kayması", barSlip,  lblSlipPct));
        p.add(Box.createVerticalStrut(5));
        p.add(buildPredRow("⛈️ Fırtına",     barStorm, lblStormPct));
        p.add(Box.createVerticalStrut(6));

        JLabel note = new JLabel("P(X≥1) = 1 − e^(−λt)  |  t = 10 dak.");
        note.setFont(new Font("Monospaced", Font.PLAIN, 9));
        note.setForeground(new Color(100, 120, 150));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(note);
        return p;
    }

    private JProgressBar createPredBar(Color color) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(0);
        bar.setForeground(color);
        bar.setBackground(new Color(20, 30, 55));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(0, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return bar;
    }

    private JLabel createPredPct() {
        JLabel l = new JLabel("0%");
        l.setFont(new Font("Monospaced", Font.BOLD, 10));
        l.setForeground(new Color(200, 220, 250));
        l.setPreferredSize(new Dimension(35, 14));
        return l;
    }

    private JPanel buildPredRow(String label, JProgressBar bar, JLabel pct) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        lbl.setForeground(new Color(160, 180, 210));
        lbl.setPreferredSize(new Dimension(110, 14));
        row.add(lbl, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        row.add(pct, BorderLayout.EAST);
        return row;
    }

    private JLabel createStateLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 10));
        l.setForeground(color);
        return l;
    }

    private JPanel createLoraLogPanel() {
        loraLogModel = new DefaultListModel<>();
        loraLogList = new JList<>(loraLogModel);
        loraLogList.setBackground(new Color(8, 12, 24));
        loraLogList.setForeground(new Color(0, 230, 118));
        loraLogList.setFont(new Font("Monospaced", Font.PLAIN, 10));
        loraLogList.setSelectionBackground(new Color(30, 50, 90));
        loraLogList.setCellRenderer(new LoraLogCellRenderer());

        JScrollPane scroll = new JScrollPane(loraLogList);
        scroll.setPreferredSize(new Dimension(0, 150));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        scroll.setBorder(new LineBorder(new Color(0, 100, 50), 1));
        scroll.getViewport().setBackground(new Color(8, 12, 24));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 155));
        p.add(scroll);
        return p;
    }

    private JPanel createEventLogPanel() {
        eventLogModel = new DefaultListModel<>();
        eventLogList = new JList<>(eventLogModel);
        eventLogList.setBackground(new Color(12, 10, 24));
        eventLogList.setForeground(new Color(220, 200, 255));
        eventLogList.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
        eventLogList.setSelectionBackground(new Color(60, 30, 90));
        eventLogList.setCellRenderer(new EventLogCellRenderer());

        JScrollPane scroll = new JScrollPane(eventLogList);
        scroll.setPreferredSize(new Dimension(0, 150));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        scroll.setBorder(new LineBorder(new Color(80, 40, 120), 1));
        scroll.getViewport().setBackground(new Color(12, 10, 24));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 155));
        p.add(scroll);
        return p;
    }

    private JPanel createExportPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new GridLayout(1, 2, 6, 0));

        btnExportNodes = createButton("💾 Node CSV", new Color(21, 101, 192));
        btnExportEvents = createButton("📋 Olay CSV", new Color(81, 45, 168));

        p.add(btnExportNodes);
        p.add(btnExportEvents);
        return p;
    }

    // ============================================================
    // GÜNCELLEME METODLARI
    // ============================================================

    public void updateWeatherInfo(double temp, double hum, double wind,
                                   double pressure, double precip, double vis,
                                   String emoji, String condition) {
        SwingUtilities.invokeLater(() -> {
            lblTemp.setText(String.format("%.1f°C", temp));
            lblHum.setText(String.format("%.0f%%", hum));
            lblWind.setText(String.format("%.1f km/h", wind));
            lblPressure.setText(String.format("%.0f hPa", pressure));
            lblPrecip.setText(String.format("%.1f mm/h", precip));
            lblVis.setText(String.format("%.1f km", vis / 1000.0));
            lblWeather.setText(emoji + " " + condition);
        });
    }

    public void addLoraLog(String message) {
        SwingUtilities.invokeLater(() -> {
            loraLogModel.add(0, message);
            if (loraLogModel.size() > 200) {
                loraLogModel.remove(loraLogModel.size() - 1);
            }
        });
    }

    public void addEventLog(RiskEvent event) {
        SwingUtilities.invokeLater(() -> {
            String msg = String.format("[%s] %s%s | %s | λ=%.1f",
                event.getFormattedTime(),
                event.getType().emoji,
                event.getType().label,
                event.getLocation(),
                event.getPoissonLambda()
            );
            eventLogModel.add(0, msg);
            if (eventLogModel.size() > 200) {
                eventLogModel.remove(eventLogModel.size() - 1);
            }
        });
    }

    public void updateEnergyStats(int active, int total, int critical, int totalEvents,
                                   boolean isDaytime, double solarFactor) {
        SwingUtilities.invokeLater(() -> {
            lblActive.setText(active + "/" + total);
            lblCritical.setText(String.valueOf(critical));
            lblTotalEvents.setText(String.valueOf(totalEvents));

            if (isDaytime) {
                String solar = String.format("☀️ Solar Aktif: %.0f%% verimlilik", solarFactor * 100);
                lblSolarStatus.setText(solar);
                lblSolarStatus.setForeground(new Color(255, 220, 50));
            } else {
                lblSolarStatus.setText("🌙 Gece: Solar kapalı, Batarya tüketimde");
                lblSolarStatus.setForeground(new Color(150, 180, 220));
            }
        });
    }

    /** Node state sayaçlarını güncelle */
    public void updateNodeStateCounts(int sleeping, int sensing, int transmitting, int emergency) {
        SwingUtilities.invokeLater(() -> {
            lblNodeSleep.setText("💤 Sleep: " + sleeping);
            lblNodeSense.setText("👁 Sense: " + sensing);
            lblNodeTx.setText("📡 TX: " + transmitting);
            lblNodeEmergency.setText("🚨 Alarm: " + emergency);
        });
    }

    /** Risk tahminlerini güncelle */
    public void updatePredictions(double fog, double rock, double slip, double storm, String advice) {
        SwingUtilities.invokeLater(() -> {
            int fogPct   = (int) Math.round(fog   * 100);
            int rockPct  = (int) Math.round(rock  * 100);
            int slipPct  = (int) Math.round(slip  * 100);
            int stormPct = (int) Math.round(storm * 100);

            barFog.setValue(fogPct);     lblFogPct.setText(fogPct   + "%");
            barRock.setValue(rockPct);   lblRockPct.setText(rockPct  + "%");
            barSlip.setValue(slipPct);   lblSlipPct.setText(slipPct  + "%");
            barStorm.setValue(stormPct); lblStormPct.setText(stormPct + "%");

            lblPredAdvice.setText(advice);

            // Renk dinamik
            recolorBar(barFog,   fogPct);
            recolorBar(barRock,  rockPct);
            recolorBar(barSlip,  slipPct);
            recolorBar(barStorm, stormPct);
        });
    }

    private void recolorBar(JProgressBar bar, int pct) {
        if (pct >= 75) bar.setForeground(new Color(244, 67, 54));
        else if (pct >= 50) bar.setForeground(new Color(255, 130, 0));
        else if (pct >= 25) bar.setForeground(new Color(255, 210, 50));
        // else keep original color
    }

    public void setExportNodesAction(ActionListener al) { btnExportNodes.addActionListener(al); }
    public void setExportEventsAction(ActionListener al) { btnExportEvents.addActionListener(al); }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    private JPanel createStatBoxPanel(String title, String value, Color color,
                                       java.util.function.Consumer<JLabel> setter) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBackground(new Color(20, 26, 46));
        box.setBorder(new LineBorder(color.darker(), 1));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Monospaced", Font.PLAIN, 9));
        titleLabel.setForeground(new Color(150, 150, 170));

        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
        valueLabel.setForeground(color);

        box.add(titleLabel, BorderLayout.NORTH);
        box.add(valueLabel, BorderLayout.CENTER);

        setter.accept(valueLabel);
        return box;
    }

    private JPanel createDarkCard() {
        JPanel p = new JPanel();
        p.setBackground(new Color(16, 22, 40));
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(40, 55, 90), 1, true),
            new EmptyBorder(8, 8, 8, 8)
        ));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel createSectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.BOLD, 11));
        l.setForeground(new Color(100, 181, 246));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel darkLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Monospaced", Font.PLAIN, 10));
        l.setForeground(new Color(140, 140, 160));
        return l;
    }

    private void styleWeatherLabel(JLabel l, Color c) {
        l.setFont(new Font("Monospaced", Font.BOLD, 12));
        l.setForeground(c);
    }

    private JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI Emoji", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ============================================================
    // CUSTOM CELL RENDERER'LAR
    // ============================================================

    private static class LoraLogCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            l.setFont(new Font("Monospaced", Font.PLAIN, 10));
            String text = value.toString();
            if (text.contains("CRITICAL") || text.contains("KRİTİK")) {
                l.setForeground(new Color(244, 67, 54));
            } else if (text.contains("DRONE")) {
                l.setForeground(new Color(129, 212, 250));
            } else {
                l.setForeground(new Color(0, 230, 118));
            }
            if (!isSelected) l.setBackground(new Color(8, 12, 24));
            l.setBorder(new EmptyBorder(1, 4, 1, 4));
            return l;
        }
    }

    private static class EventLogCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            l.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 11));
            String text = value.toString();
            if (text.contains("Kaya") || text.contains("Heyelan")) {
                l.setForeground(new Color(255, 138, 101));
            } else if (text.contains("Sis")) {
                l.setForeground(new Color(144, 202, 249));
            } else if (text.contains("Kaygan")) {
                l.setForeground(new Color(255, 235, 59));
            } else if (text.contains("Rüzgar")) {
                l.setForeground(new Color(128, 203, 196));
            } else {
                l.setForeground(new Color(220, 200, 255));
            }
            if (!isSelected) l.setBackground(new Color(12, 10, 24));
            l.setBorder(new EmptyBorder(2, 4, 2, 4));
            return l;
        }
    }
}
