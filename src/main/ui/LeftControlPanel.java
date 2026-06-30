package main.ui;

import main.simulation.PoissonEngine;
import main.simulation.SimulationController;
import main.util.Constants;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/**
 * Sol Kontrol Paneli
 * - Simülasyon başlat/durdur/sıfırla
 * - λ (lambda) slider
 * - Hava durumu seçimi
 * - Sistem bilgisi
 */
public class LeftControlPanel extends JPanel {

    private final SimulationController sim;

    private JButton btnStart, btnStop, btnReset;
    private JLabel lblLambda, lblLambdaMath;
    private JLabel lblExpected, lblVariance;
    private JLabel lblWeatherIcon;
    private JLabel lblApiStatus;
    private JLabel lblTime, lblDate;
    private JProgressBar simProgress;
    private JLabel lblNodeCount, lblCriticalCount;
    private JLabel lblEvents, lblDrones;
    private Timer clockTimer;

    // Callback'ler
    private Runnable onStart, onStop, onReset;
    private Consumer<Double> onLambdaChange;
    private Consumer<String> onWeatherChange;

    public LeftControlPanel(SimulationController sim) {
        this.sim = sim;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(10, 15, 30));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(260, 0));

        buildUI();
        startClock();
    }

    private void buildUI() {
        // Logo / Başlık
        add(createTitlePanel());
        add(Box.createVerticalStrut(12));

        // Saat ve Tarih
        add(createClockPanel());
        add(Box.createVerticalStrut(12));

        // Simülasyon Kontrolleri
        add(createSectionLabel("⚙️ SİMÜLASYON KONTROLÜ"));
        add(Box.createVerticalStrut(6));
        add(createSimControlPanel());
        add(Box.createVerticalStrut(14));

        // Lambda Ayarı
        add(createSectionLabel("📊 POİSSON PARAMETRESI (λ)"));
        add(Box.createVerticalStrut(6));
        add(createLambdaPanel());
        add(Box.createVerticalStrut(14));

        // Hava Durumu
        add(createSectionLabel("🌤️ HAVA DURUMU"));
        add(Box.createVerticalStrut(6));
        add(createWeatherPanel());
        add(Box.createVerticalStrut(14));

        // Durum Özeti
        add(createSectionLabel("📈 DURUM ÖZETİ"));
        add(Box.createVerticalStrut(6));
        add(createStatusPanel());
        add(Box.createVerticalStrut(14));

        // Datasheet Bilgisi
        add(createSectionLabel("🔌 DONANIM (FiPy)"));
        add(Box.createVerticalStrut(6));
        add(createHardwareInfoPanel());

        add(Box.createVerticalGlue());
    }

    // ============================================================
    // UI BİLEŞENLERİ
    // ============================================================

    private JPanel createTitlePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JLabel title = new JLabel("<html><center>" +
            "<span style='color:#64B5F6;font-size:13px;font-weight:bold'>MERSIN–ANTALYA</span><br>" +
            "<span style='color:#90A4AE;font-size:10px'>Akıllı Risk Erken Uyarı Sistemi</span><br>" +
            "<span style='color:#546E7A;font-size:9px'>Digital Twin WSN Simülatörü</span>" +
            "</center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(title, BorderLayout.CENTER);

        // API durum göstergesi
        lblApiStatus = new JLabel("● API Bağlanıyor...");
        lblApiStatus.setForeground(new Color(255, 193, 7));
        lblApiStatus.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lblApiStatus.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(lblApiStatus, BorderLayout.SOUTH);

        return p;
    }

    private JPanel createClockPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new GridLayout(2, 1, 0, 2));

        lblTime = new JLabel("--:--:--", SwingConstants.CENTER);
        lblTime.setFont(new Font("Monospaced", Font.BOLD, 22));
        lblTime.setForeground(new Color(100, 181, 246));

        lblDate = new JLabel("----.--.--", SwingConstants.CENTER);
        lblDate.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblDate.setForeground(new Color(144, 164, 174));

        p.add(lblTime);
        p.add(lblDate);
        return p;
    }

    private JPanel createSimControlPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new GridLayout(3, 1, 4, 4));

        btnStart = createButton("▶  Simülasyonu Başlat", new Color(0, 150, 136), Color.WHITE);
        btnStop  = createButton("⏹  Durdur", new Color(183, 28, 28), Color.WHITE);
        btnReset = createButton("↺  Sıfırla", new Color(37, 41, 61), new Color(144, 164, 174));

        btnStop.setEnabled(false);

        btnStart.addActionListener(e -> {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            if (onStart != null) onStart.run();
        });

        btnStop.addActionListener(e -> {
            btnStop.setEnabled(false);
            btnStart.setEnabled(true);
            if (onStop != null) onStop.run();
        });

        btnReset.addActionListener(e -> {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            if (onReset != null) onReset.run();
        });

        p.add(btnStart);
        p.add(btnStop);
        p.add(btnReset);
        return p;
    }

    private JPanel createLambdaPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Matematiksel açıklama
        JLabel formula = new JLabel("<html><center>" +
            "<span style='color:#CE93D8;font-size:11px'>P(X=k) = e⁻λ · λᵏ / k!</span><br>" +
            "<span style='color:#78909C;font-size:9px'>API Değerlerinden Otomatik Matematiksel Hesaplama</span>" +
            "</center></html>");
        formula.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Lambda değeri
        lblLambda = new JLabel("λ = 0.50", SwingConstants.CENTER);
        lblLambda.setFont(new Font("Monospaced", Font.BOLD, 20));
        lblLambda.setForeground(new Color(100, 181, 246));
        lblLambda.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Beklenen değer ve varyans
        lblLambdaMath = new JLabel("E[X]=0.50  σ=0.71", SwingConstants.CENTER);
        lblLambdaMath.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lblLambdaMath.setForeground(new Color(144, 164, 174));
        lblLambdaMath.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel info = new JLabel("<html><center>" +
            "<span style='color:#546E7A;font-size:9px'>λ oranı; rota geneli anlık yağış, nem, görüş mesafesi ve rüzgar API verilerine bağlı olarak dinamik hesaplanır.</span>" +
            "</center></html>");
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(formula);
        p.add(Box.createVerticalStrut(6));
        p.add(lblLambda);
        p.add(lblLambdaMath);
        p.add(Box.createVerticalStrut(6));
        p.add(info);
        return p;
    }

    private JPanel createWeatherPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("🌍 GERÇEK ZAMANLI METEOROLOJİ", SwingConstants.CENTER);
        title.setFont(new Font("Monospaced", Font.BOLD, 10));
        title.setForeground(new Color(129, 199, 132));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblWeatherIcon = new JLabel("☀️ Yükleniyor...", SwingConstants.CENTER);
        lblWeatherIcon.setFont(new Font("Segoe UI Emoji", Font.BOLD, 15));
        lblWeatherIcon.setForeground(Color.WHITE);
        lblWeatherIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("<html><center>Anamur İstasyonu (Mersin-Antalya Sahil Şeridi Bölge Merkezi)</center></html>");
        desc.setFont(new Font("Monospaced", Font.PLAIN, 9));
        desc.setForeground(new Color(100, 120, 140));
        desc.setPreferredSize(new Dimension(200, 30));
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(title);
        p.add(Box.createVerticalStrut(8));
        p.add(lblWeatherIcon);
        p.add(Box.createVerticalStrut(6));
        p.add(desc);
        return p;
    }

    private JPanel createStatusPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new GridLayout(4, 2, 4, 6));

        p.add(darkLabel("Aktif Node:"));
        lblNodeCount = darkLabel("0 / 45");
        lblNodeCount.setForeground(new Color(100, 181, 246));
        p.add(lblNodeCount);

        p.add(darkLabel("Kritik Alarm:"));
        lblCriticalCount = darkLabel("0");
        lblCriticalCount.setForeground(new Color(244, 67, 54));
        p.add(lblCriticalCount);

        p.add(darkLabel("Toplam Olay:"));
        lblEvents = darkLabel("0");
        lblEvents.setForeground(new Color(255, 193, 7));
        p.add(lblEvents);

        p.add(darkLabel("Drone Görev:"));
        lblDrones = darkLabel("0");
        lblDrones.setForeground(new Color(129, 212, 250));
        p.add(lblDrones);

        return p;
    }

    private JPanel createHardwareInfoPanel() {
        JPanel p = createDarkCard();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        String[][] info = {
            {"MCU:", "Pycom FiPy"},
            {"Sleep:", String.format("%.0f µA", Constants.FIPY_SLEEP_CURRENT_MA * 1000)},
            {"LoRa TX:", String.format("%.0f mA", Constants.FIPY_LORA_TX_MA)},
            {"Batarya:", String.format("%.0f mAh@%.1fV", Constants.FIPY_BATTERY_MAH, Constants.FIPY_VOLTAGE_V)},
            {"BME280:", "3.6 µA (normal)"},
            {"MPU-6050:", "3.9 mA (normal)"},
            {"Solar:", "5V 100mA panel"},
            {"LoRa Band:", "EU868 MHz"},
        };

        for (String[] row : info) {
            JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
            rowPanel.setOpaque(false);
            JLabel key = new JLabel(row[0]);
            key.setFont(new Font("Monospaced", Font.PLAIN, 10));
            key.setForeground(new Color(100, 100, 130));
            JLabel val = new JLabel(row[1]);
            val.setFont(new Font("Monospaced", Font.PLAIN, 10));
            val.setForeground(new Color(144, 200, 174));
            rowPanel.add(key, BorderLayout.WEST);
            rowPanel.add(val, BorderLayout.EAST);
            p.add(rowPanel);
        }
        return p;
    }

    // ============================================================
    // GÜNCELLEME METODLARI (simülasyondan çağrılır)
    // ============================================================

    public void updateStatus(int active, int total, int critical, int events, int drones) {
        SwingUtilities.invokeLater(() -> {
            lblNodeCount.setText(active + " / " + total);
            lblCriticalCount.setText(String.valueOf(critical));
            lblEvents.setText(String.valueOf(events));
            lblDrones.setText(String.valueOf(drones));
        });
    }

    public void updateWeatherDisplay(String emoji, String description, boolean apiOk) {
        SwingUtilities.invokeLater(() -> {
            lblWeatherIcon.setText(emoji + " " + description);
            if (apiOk) {
                lblApiStatus.setText("● Open-Meteo API Aktif");
                lblApiStatus.setForeground(new Color(76, 175, 80));
            } else {
                lblApiStatus.setText("● Simüle Mod (Offline)");
                lblApiStatus.setForeground(new Color(255, 193, 7));
            }
        });
    }

    public void updateLambdaDisplay(double lambda) {
        SwingUtilities.invokeLater(() -> {
            lblLambda.setText(String.format("λ = %.2f", lambda));
            lblLambdaMath.setText(String.format("E[X]=%.2f  σ=%.2f", lambda, Math.sqrt(lambda)));
        });
    }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    private void startClock() {
        clockTimer = new Timer(1000, e -> {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            lblTime.setText(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            lblDate.setText(now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy  EEEE",
                new java.util.Locale("tr", "TR"))));
        });
        clockTimer.start();
    }

    private JPanel createDarkCard() {
        JPanel p = new JPanel();
        p.setBackground(new Color(16, 22, 40));
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(40, 55, 90), 1, true),
            new EmptyBorder(8, 10, 8, 10)
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
        l.setFont(new Font("Monospaced", Font.PLAIN, 11));
        l.setForeground(new Color(180, 180, 200));
        return l;
    }

    private JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(bg.brighter());
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }

    // Callback setters
    public void setOnStart(Runnable r) { this.onStart = r; }
    public void setOnStop(Runnable r) { this.onStop = r; }
    public void setOnReset(Runnable r) { this.onReset = r; }
    public void setOnLambdaChange(Consumer<Double> c) { this.onLambdaChange = c; }
    public void setOnWeatherChange(Consumer<String> c) { this.onWeatherChange = c; }
}
