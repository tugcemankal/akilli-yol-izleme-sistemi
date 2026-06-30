package main;

import main.model.DroneUnit;
import main.model.RiskEvent;
import main.model.SensorNode;
import main.simulation.SimulationController;
import main.ui.BottomChartPanel;
import main.ui.LeftControlPanel;
import main.ui.MapServerPanel;
import main.ui.RightStatsPanel;
import main.ui.SwingMapPanel;
import main.util.CSVExporter;


import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * Ana Swing Penceresi
 * 
 * Layout:
 * +----------------------------------------------------------+
 * |                    Başlık Çubuğu                        |
 * +------------------+--------------------+-----------------+
 * | Sol Kontrol      | Harita Server Info | Sağ İstatistik  |
 * | Paneli           | (Tarayıcıda açık)  | & Log Paneli    |
 * +------------------+--------------------+-----------------+
 * |           Alt Grafik Paneli (JFreeChart)                 |
 * +----------------------------------------------------------+
 */
public class MainFrame extends JFrame {

    private final SimulationController sim;
    private MapServerPanel mapServerPanel;
    private LeftControlPanel leftPanel;
    private RightStatsPanel rightPanel;
    private BottomChartPanel bottomPanel;
    private SwingMapPanel swingMapPanel;
    private JTabbedPane midTabbedPane;
    private Timer uiUpdateTimer;
    private JToggleButton btnToggleLeft;
    private JToggleButton btnToggleRight;
    private JToggleButton btnToggleBottom;


    public MainFrame() {
        this.sim = new SimulationController();
        buildFrame();
        setupSimulationListeners();
        setupUITimer();
    }

    private void buildFrame() {
        setTitle("🛡️ Mersin–Antalya Sahil Yolu - Akıllı Risk Erken Uyarı Sistemi | Digital Twin WSN");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1400, 800));
        setPreferredSize(new Dimension(1600, 950));
        getContentPane().setBackground(new Color(8, 12, 24));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        setLayout(new BorderLayout(0, 0));

        // Üst başlık
        add(createTitleBar(), BorderLayout.NORTH);

        // Orta: Sol + Harita bilgisi + Sağ
        JPanel centerPanel = new JPanel(new BorderLayout(4, 0));
        centerPanel.setBackground(new Color(8, 12, 24));
        centerPanel.setBorder(new EmptyBorder(4, 4, 0, 4));

        // Sol kontrol paneli
        leftPanel = new LeftControlPanel(sim);
        centerPanel.add(leftPanel, BorderLayout.WEST);

        // Orta: Harita server paneli + büyük bir bilgi alanı
        JPanel midPanel = buildMidPanel();
        centerPanel.add(midPanel, BorderLayout.CENTER);

        // Sağ istatistik paneli
        rightPanel = new RightStatsPanel(sim);
        centerPanel.add(rightPanel, BorderLayout.EAST);

        add(centerPanel, BorderLayout.CENTER);

        // Alt grafikler
        bottomPanel = new BottomChartPanel(sim);
        add(bottomPanel, BorderLayout.SOUTH);

        // İlk açılışta haritayı daha temiz göstermek için
        leftPanel.setVisible(false);
        rightPanel.setVisible(false);
        bottomPanel.setVisible(true);

        // Callback'leri bağla
        setupPanelCallbacks();

        pack();
        setLocationRelativeTo(null);
        updateWeatherDisplay();
    }

    private JPanel buildMidPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(new Color(8, 12, 24));

        // Harita server paneli (tarayıcıda harita açar)
        mapServerPanel = new MapServerPanel(sim);
        p.add(mapServerPanel, BorderLayout.NORTH);

        // Tabbed Pane ile Harita ve Datasheet Sekmeleri
        midTabbedPane = new JTabbedPane();
        midTabbedPane.setBackground(new Color(16, 22, 40));
        midTabbedPane.setForeground(new Color(220, 220, 230));
        midTabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Sekme 1: Canlı Harita
        swingMapPanel = new SwingMapPanel(sim);
        midTabbedPane.addTab("🗺️ Canlı Harita (Digital Twin)", swingMapPanel);

        // Sekme 2: Donanım Datasheetleri & Poisson
        midTabbedPane.addTab("📋 Donanım Datasheetleri & Matematik", buildInfoPanel());

        p.add(midTabbedPane, BorderLayout.CENTER);

        return p;
    }


    private JPanel buildInfoPanel() {
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.setBackground(new Color(8, 12, 24));
        p.setBorder(new EmptyBorder(0, 0, 4, 0));

        // Kart 1: Pycom FiPy Datasheet
        p.add(buildCard("🔌 Pycom FiPy Datasheet",
            "Sleep Mode:     10 µA\n" +
            "Modem Sleep:    1.2 mA\n" +
            "Active (CPU):   45 mA\n" +
            "LoRa TX (+20dBm): 120 mA\n" +
            "LoRa RX:        15 mA\n" +
            "WiFi TX:        80 mA\n" +
            "Batarya:        3.7V 2000mAh\n" +
            "Batarya (J):    26,640 J\n" +
            "LoRa Band:      EU868 MHz",
            new Color(100, 181, 246)));

        // Kart 2: Bosch BME280
        p.add(buildCard("🌡️ Bosch BME280 Sensör",
            "Normal Mod:     3.6 µA\n" +
            "Uyku Modu:      0.1 µA\n" +
            "Forced Mod:     2.0 µA\n" +
            "Sıcaklık:       -40 ~ +85°C\n" +
            "Basınç:         300 ~ 1100 hPa\n" +
            "Nem:            0 ~ 100% RH\n" +
            "Arayüz:         I2C / SPI\n" +
            "Ölçüm Süresi:   2 ms\n" +
            "Besleme:        1.71 ~ 3.6V",
            new Color(129, 199, 132)));

        // Kart 3: MPU-6050
        p.add(buildCard("📳 InvenSense MPU-6050",
            "Normal Mod:     3.9 mA\n" +
            "Sadece Acc:     0.5 mA\n" +
            "Uyku Modu:      5 µA\n" +
            "İvme Aralığı:   ±2/4/8/16 g\n" +
            "Jiroskop:       ±250~2000°/s\n" +
            "Arayüz:         I2C\n" +
            "Besleme:        2.375 ~ 3.46V\n" +
            "Gürültü:        400 µg/√Hz\n" +
            "DOF:            6 eksen",
            new Color(206, 147, 216)));

        // Kart 4: Poisson Modeli Açıklama
        p.add(buildCard("📐 Poisson Dağılımı Modeli",
            "P(X=k) = e^(-λ) * λ^k / k!\n" +
            "\n" +
            "λ (lambda): Birim zaman olay sayısı\n" +
            "k: Gerçekleşen olay sayısı\n" +
            "E[X] = λ (Beklenen değer)\n" +
            "Var[X] = λ (Varyans)\n" +
            "\n" +
            "Hava koşulu → λ eşleşmesi:\n" +
            "  Güneşli     λ = 0.8\n" +
            "  Yağmurlu    λ = 3.5\n" +
            "  Fırtınalı   λ = 7.0\n" +
            "  Sisli       λ = 5.5",
            new Color(255, 167, 38)));

        return p;
    }

    private JPanel buildCard(String title, String content, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(new Color(14, 20, 38));
        card.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(accentColor.getRed()/3, accentColor.getGreen()/3, accentColor.getBlue()/3, 180), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        titleLabel.setForeground(accentColor);

        JTextArea text = new JTextArea(content);
        text.setFont(new Font("Monospaced", Font.PLAIN, 11));
        text.setForeground(new Color(180, 190, 210));
        text.setBackground(new Color(14, 20, 38));
        text.setEditable(false);
        text.setBorder(null);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(new Color(8, 14, 28));
        bar.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 2, 0, new Color(30, 60, 100)),
            new EmptyBorder(8, 14, 8, 14)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel logo = new JLabel("🛡️");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));

        JLabel title = new JLabel("<html>" +
            "<span style='color:#64B5F6;font-size:15px;font-weight:bold'>" +
            "Mersin–Antalya Sahil Yolu Akıllı Risk Erken Uyarı Sistemi</span><br>" +
            "<span style='color:#546E7A;font-size:10px'>" +
            "Poisson Tabanlı Kablosuz Algılayıcı Ağ (WSN) · Digital Twin · LoRaWAN · Pycom FiPy + BME280 + MPU-6050 · Open-Meteo API</span>" +
            "</html>");

        left.add(logo);
        left.add(title);

        JPanel centerControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerControls.setOpaque(false);

        btnToggleLeft = createToggleButton("⚙ Kontrol", false);
        btnToggleRight = createToggleButton("📊 İstatistik", false);
        btnToggleBottom = createToggleButton("📈 Grafikler", true);

        btnToggleLeft.addActionListener(e -> {
            leftPanel.setVisible(btnToggleLeft.isSelected());
            revalidate();
            repaint();
        });
        btnToggleRight.addActionListener(e -> {
            rightPanel.setVisible(btnToggleRight.isSelected());
            revalidate();
            repaint();
        });
        btnToggleBottom.addActionListener(e -> {
            bottomPanel.setVisible(btnToggleBottom.isSelected());
            revalidate();
            repaint();
        });

        centerControls.add(btnToggleLeft);
        centerControls.add(btnToggleRight);
        centerControls.add(btnToggleBottom);

        JLabel badge = new JLabel("v1.1 | Sade Arayüz");
        badge.setFont(new Font("Monospaced", Font.PLAIN, 10));
        badge.setForeground(new Color(80, 100, 130));

        bar.add(left, BorderLayout.WEST);
        bar.add(centerControls, BorderLayout.CENTER);
        bar.add(badge, BorderLayout.EAST);
        return bar;
    }

    private JToggleButton createToggleButton(String text, boolean selected) {
        JToggleButton btn = new JToggleButton(text, selected);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBackground(selected ? new Color(33, 150, 243) : new Color(24, 32, 52));
        btn.setForeground(selected ? Color.WHITE : new Color(170, 190, 220));
        btn.setOpaque(true);
        btn.setMargin(new Insets(5, 10, 5, 10));
        btn.addItemListener(e -> {
            boolean on = btn.isSelected();
            btn.setBackground(on ? new Color(33, 150, 243) : new Color(24, 32, 52));
            btn.setForeground(on ? Color.WHITE : new Color(170, 190, 220));
        });
        return btn;
    }

    private void setupPanelCallbacks() {
        leftPanel.setOnStart(() -> {
            // PC 2'nin IP adresini sor
            String ip = (String) JOptionPane.showInputDialog(
                this,
                "<html><b>PC 2'nin IP adresini girin:</b><br>" +
                "<small>PC 2'de <code>ipconfig</code> komutu ile öğrenin</small><br><br>" +
                "Sadece bu PC'de test etmek için: <code>127.0.0.1</code></html>",
                "🌐 Ağ Ayarı — PC 2 IP",
                JOptionPane.QUESTION_MESSAGE,
                null, null,
                sim.getPc2IpAddress()
            );
            if (ip != null && !ip.trim().isEmpty()) {
                sim.setPc2IpAddress(ip.trim());
            }
            sim.start();
        });

        leftPanel.setOnStop(() -> {
            sim.stop();
        });

        leftPanel.setOnReset(() -> {
            sim.reset();
        });

        leftPanel.setOnLambdaChange(lambda -> {
            sim.getPoissonEngine().setLambda(lambda);
            bottomPanel.updatePoissonData(lambda);
        });

        leftPanel.setOnWeatherChange(condition -> {
            if (!condition.equals("REALTIME")) {
                sim.getPoissonEngine().updateLambdaForWeather(condition);
                double newLambda = sim.getPoissonEngine().getLambda();
                leftPanel.updateLambdaDisplay(newLambda);
                bottomPanel.updatePoissonData(newLambda);
            }
        });

        rightPanel.setExportNodesAction(e -> {
            try {
                String file = CSVExporter.exportNodeStatus(sim.getNodes());
                JOptionPane.showMessageDialog(this,
                    "✅ Node raporu kaydedildi:\n" + file,
                    "Dışa Aktarma Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "❌ Hata: " + ex.getMessage(), "Export Hatası", JOptionPane.ERROR_MESSAGE);
            }
        });

        rightPanel.setExportEventsAction(e -> {
            try {
                String file = CSVExporter.exportEvents(sim.getGlobalEventLog());
                JOptionPane.showMessageDialog(this,
                    "✅ Olay raporu kaydedildi:\n" + file,
                    "Dışa Aktarma Başarılı", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "❌ Hata: " + ex.getMessage(), "Export Hatası", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void setupSimulationListeners() {
        sim.addListener(new SimulationController.SimulationListener() {
            @Override public void onNodeActivated(SensorNode node) {}
            @Override public void onRiskUpdated(SensorNode node) {}
            @Override public void onDroneDispatched(DroneUnit drone, SensorNode target) {}
            @Override public void onSimulationTick(long tickCount) {}

            @Override
            public void onEventGenerated(RiskEvent event, SensorNode node) {
                SwingUtilities.invokeLater(() -> rightPanel.addEventLog(event));
            }

            @Override
            public void onWeatherUpdated() {
                SwingUtilities.invokeLater(() -> updateWeatherDisplay());
            }

            @Override
            public void onLoraMessage(String message) {
                SwingUtilities.invokeLater(() -> rightPanel.addLoraLog(message));
            }
        });
    }

    private void setupUITimer() {
        uiUpdateTimer = new Timer(1000, e -> {
            int active   = sim.getActiveNodeCount();
            int total    = sim.getNodes().size();
            int critical = sim.getCriticalNodeCount();
            int events   = sim.getTotalEventsGlobal();
            int drones   = sim.getDronesDispatched();

            leftPanel.updateStatus(active, total, critical, events, drones);

            if (sim.isRunning()) {
                double lambda = sim.getPoissonEngine().getLambda();
                leftPanel.updateLambdaDisplay(lambda);
                bottomPanel.updateCharts(sim.getTickCount(), events, sim.getNodes());
                bottomPanel.updatePoissonData(lambda);
                updateWeatherDisplay();

                // Node durum sayıları
                long sleeping     = sim.getNodes().stream().filter(n -> n.isActive() && n.getState() == main.model.SensorNode.NodeState.SLEEPING).count();
                long sensing      = sim.getNodes().stream().filter(n -> n.isActive() && n.getState() == main.model.SensorNode.NodeState.SENSING).count();
                long transmitting = sim.getNodes().stream().filter(n -> n.isActive() && n.getState() == main.model.SensorNode.NodeState.TRANSMITTING).count();
                long emergency    = sim.getNodes().stream().filter(n -> n.isActive() && n.getRiskLevel() == main.model.SensorNode.RiskLevel.CRITICAL).count();
                rightPanel.updateNodeStateCounts((int)sleeping, (int)sensing, (int)transmitting, (int)emergency);

                // Risk tahminleri
                var pred = sim.getRiskPredictor();
                pred.update();
                rightPanel.updatePredictions(
                    pred.getFogProbability(),
                    pred.getRockProbability(),
                    pred.getSlipProbability(),
                    pred.getStormProbability(),
                    pred.getAdvice()
                );

                // Enerji istatistikleri
                rightPanel.updateEnergyStats(active, total, critical, events,
                    sim.getWeatherService().isDaytime(),
                    sim.getWeatherService().getSolarChargeFactor());
            }
        });
        uiUpdateTimer.start();
    }

    private void updateWeatherDisplay() {
        var weather = sim.getWeatherService();
        leftPanel.updateWeatherDisplay(
            weather.getWeatherEmoji(),
            weather.getWeatherDescription(),
            weather.isApiAvailable()
        );
        rightPanel.updateWeatherInfo(
            weather.getTemperature(),
            weather.getHumidity(),
            weather.getWindSpeed(),
            weather.getPressure(),
            weather.getPrecipitation(),
            weather.getVisibility(),
            weather.getWeatherEmoji(),
            weather.getWeatherDescription()
        );
    }

    private void onClose() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Simülasyonu kapatmak istediğinizden emin misiniz?",
            "Çıkış Onayı", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            sim.stop();
            if (uiUpdateTimer != null) uiUpdateTimer.stop();
            if (mapServerPanel != null) mapServerPanel.stopServer();
            System.exit(0);
        }
    }
}
