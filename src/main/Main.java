package main;

import javax.swing.*;
import java.awt.*;

/**
 * Uygulama Giriş Noktası
 * 
 * Mersin–Antalya Sahil Yolu Akıllı Risk Erken Uyarı Sistemi
 * Poisson Tabanlı Kablosuz Algılayıcı Ağ Digital Twin
 * 
 * Geliştirici: Öğrenci Projesi - Olasılık ve İstatistik
 * Donanım Referansı: Pycom FiPy + Bosch BME280 + InvenSense MPU-6050
 */
public class Main {
    public static void main(String[] args) {
        // Yüksek DPI desteği
        System.setProperty("sun.java2d.uiScale", "1.0");

        // Look and Feel: karanlık tema
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Tüm UI tweakları
        UIManager.put("Panel.background", new Color(10, 15, 30));
        UIManager.put("Label.foreground", new Color(220, 220, 230));
        UIManager.put("Button.background", new Color(30, 40, 70));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("ComboBox.background", new Color(20, 26, 46));
        UIManager.put("ComboBox.foreground", new Color(220, 220, 230));
        UIManager.put("ScrollBar.thumb", new Color(40, 55, 90));
        UIManager.put("ScrollBar.track", new Color(16, 22, 40));
        UIManager.put("OptionPane.background", new Color(16, 22, 40));
        UIManager.put("OptionPane.messageForeground", new Color(220, 220, 230));

        SwingUtilities.invokeLater(() -> {
            // Splash ekranı göster
            showSplash();
        });
    }

    private static void showSplash() {
        JWindow splash = new JWindow();
        splash.getContentPane().setBackground(new Color(8, 12, 24));
        splash.setLayout(new BorderLayout());
        splash.setSize(600, 350);
        splash.setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(new Color(8, 12, 24));
        content.setBorder(new javax.swing.border.EmptyBorder(40, 60, 40, 60));

        // Başlık
        JLabel title = new JLabel("<html><center>" +
            "<div style='color:#64B5F6;font-size:18px;font-weight:bold'>🛡️ MERSIN–ANTALYA</div>" +
            "<div style='color:#90CAF9;font-size:13px'>Sahil Yolu Akıllı Risk Erken Uyarı Sistemi</div>" +
            "</center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = new JLabel("<html><center>" +
            "<div style='color:#546E7A;font-size:11px'>" +
            "Poisson Tabanlı Kablosuz Algılayıcı Ağ | Digital Twin<br>" +
            "LoRaWAN · Pycom FiPy · BME280 · MPU-6050<br>" +
            "Open-Meteo Gerçek Zamanlı Hava API" +
            "</div></center></html>");
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progress = new JProgressBar(0, 100);
        progress.setBackground(new Color(20, 28, 50));
        progress.setForeground(new Color(100, 181, 246));
        progress.setStringPainted(false);
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(0, 4));

        JLabel status = new JLabel("Sistem başlatılıyor...", SwingConstants.CENTER);
        status.setFont(new Font("Monospaced", Font.PLAIN, 11));
        status.setForeground(new Color(100, 120, 150));

        content.add(title, BorderLayout.NORTH);
        content.add(subtitle, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        bottom.add(progress, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        splash.add(content);
        splash.setVisible(true);

        // Yükleme animasyonu
        Timer loadTimer = new Timer(30, null);
        final int[] pct = {0};
        final String[] steps = {
            "Node koordinatları yükleniyor...",
            "Poisson motoru başlatılıyor...",
            "Enerji modeli hazırlanıyor...",
            "Hava servisi bağlanıyor...",
            "Leaflet haritası hazırlanıyor...",
            "JFreeChart grafikleri kurulumlanıyor...",
            "LoRaWAN protokolü başlatılıyor...",
            "Drone sistemleri hazırlanıyor...",
            "Simülasyon hazır!"
        };

        loadTimer.addActionListener(e -> {
            pct[0] += 2;
            progress.setValue(pct[0]);

            int stepIdx = (pct[0] / 12);
            if (stepIdx < steps.length) {
                status.setText(steps[stepIdx]);
            }

            if (pct[0] >= 100) {
                loadTimer.stop();
                splash.setVisible(false);
                splash.dispose();

                // Ana pencereyi aç
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            }
        });
        loadTimer.start();
    }
}
