package main.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Sentetik Dataset Üreteci — Gerçekçi (Örtüşümlü) Dağılımlar
 *
 * Çalıştır: generate-dataset.bat
 * Çıktı:    dataset.csv (700 satır, 3 sınıf dengeli)
 *
 * Sınıf dağılımı:
 *   SAFE         → 300 örnek  (güneşli/sakin trafik)
 *   FOG_SLIPPERY → 200 örnek  (sisli/yağmurlu trafik)
 *   ROCK_FALL    → 200 örnek  (fırtınalı burst trafik)
 *
 * GERÇEKÇİLİK: Dağılımlar kasıtlı olarak ÖRTÜŞÜYOR.
 * Beklenen doğruluk (10-fold CV): J48 ~88-93%, NB ~82-88%
 * Konfüzyon matrisinde yanlış sınıflandırmalar görünür.
 */
public class DatasetGenerator {

    private static final Random RNG = new Random(42);
    private static final String OUTPUT_FILE = "dataset.csv";
    private static final SimpleDateFormat SDF =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws IOException {
        System.out.println("============================================");
        System.out.println("  D400 Sentetik Dataset Ureteci");
        System.out.println("  Gercekci (Ortusumlu) Dagilimlar");
        System.out.println("============================================");

        List<String[]> rows = new ArrayList<>();

        // ── SAFE: 300 örnek ────────────────────────────────────────
        // Düşük trafik | avg_size 40-170 (FOG ile ÖRTÜŞME: 100-170)
        System.out.println("[1/3] SAFE ornekleri uretiliyor... (300)");
        for (int i = 0; i < 300; i++) {
            double avgSize     = gauss(85.0,  35.0,  40.0, 170.0);
            double bytesPerSec = gauss(900.0, 450.0, 100.0, 2200.0);
            double jitterMs    = gauss(6.0,   5.0,   0.5,   22.0);
            int    packetCount = (int) gauss(38.0,  8.0,   8.0,   45.0);
            // %10 gürültü: bazı SAFE paketleri FOG bölgesine girer
            if (RNG.nextDouble() < 0.10) {
                avgSize     = gauss(150.0, 30.0, 100.0, 230.0);
                bytesPerSec = gauss(2000.0, 500.0, 1000.0, 3500.0);
                jitterMs    = gauss(22.0, 8.0, 10.0, 42.0);
            }
            rows.add(row(avgSize, bytesPerSec, jitterMs, packetCount, "SAFE"));
        }

        // ── FOG_SLIPPERY: 200 örnek ────────────────────────────────
        // Orta trafik | avg_size 90-420 (SAFE örtüşme: 90-170, ROCK örtüşme: 300-420)
        System.out.println("[2/3] FOG_SLIPPERY ornekleri uretiliyor... (200)");
        for (int i = 0; i < 200; i++) {
            double avgSize     = gauss(220.0,  80.0,  90.0,  420.0);
            double bytesPerSec = gauss(3000.0, 1100.0, 700.0, 5800.0);
            double jitterMs    = gauss(35.0,   18.0,   8.0,   80.0);
            int    packetCount = (int) gauss(22.0,  8.0,  6.0, 40.0);
            double r = RNG.nextDouble();
            // %8 gürültü → SAFE bölgesine kayar
            if (r < 0.08) {
                avgSize     = gauss(90.0, 20.0, 55.0, 145.0);
                bytesPerSec = gauss(1000.0, 300.0, 500.0, 1900.0);
                jitterMs    = gauss(8.0, 4.0, 2.0, 18.0);
            }
            // %8 gürültü → ROCK bölgesine kayar
            else if (r < 0.16) {
                avgSize     = gauss(400.0, 80.0, 270.0, 600.0);
                bytesPerSec = gauss(5500.0, 1000.0, 3500.0, 8000.0);
                jitterMs    = gauss(68.0, 15.0, 40.0, 105.0);
            }
            rows.add(row(avgSize, bytesPerSec, jitterMs, packetCount, "FOG_SLIPPERY"));
        }

        // ── ROCK_FALL: 200 örnek ────────────────────────────────────
        // Yüksek burst | avg_size 250-1000 (FOG ile ÖRTÜŞME: 250-420)
        System.out.println("[3/3] ROCK_FALL ornekleri uretiliyor... (200)");
        for (int i = 0; i < 200; i++) {
            double avgSize     = gauss(550.0, 180.0, 250.0, 1000.0);
            double bytesPerSec = gauss(8000.0, 2500.0, 3000.0, 15000.0);
            double jitterMs    = gauss(90.0,   30.0,  30.0,  170.0);
            int    packetCount = (int) gauss(28.0, 10.0, 8.0, 55.0);
            // %10 gürültü → FOG bölgesine kayar
            if (RNG.nextDouble() < 0.10) {
                avgSize     = gauss(280.0, 60.0, 160.0, 430.0);
                bytesPerSec = gauss(4000.0, 800.0, 2500.0, 6000.0);
                jitterMs    = gauss(50.0, 15.0, 25.0, 82.0);
            }
            rows.add(row(avgSize, bytesPerSec, jitterMs, packetCount, "ROCK_FALL"));
        }

        Collections.shuffle(rows, RNG);

        // CSV yaz
        File outFile = new File(OUTPUT_FILE);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(outFile)))) {
            pw.println("window_id,timestamp,avg_size,bytes_per_sec,jitter_ms,packet_count,label");
            long baseTime = System.currentTimeMillis() - (long) rows.size() * 5000;
            for (int i = 0; i < rows.size(); i++) {
                String[] r = rows.get(i);
                String ts  = SDF.format(new Date(baseTime + (long) i * 5000));
                pw.printf(Locale.US, "%d,%s,%s,%s,%s,%s,%s%n",
                    i + 1, ts, r[0], r[1], r[2], r[3], r[4]);
            }
        }

        long safeCount = rows.stream().filter(r -> r[4].equals("SAFE")).count();
        long fogCount  = rows.stream().filter(r -> r[4].equals("FOG_SLIPPERY")).count();
        long rockCount = rows.stream().filter(r -> r[4].equals("ROCK_FALL")).count();

        System.out.println();
        System.out.println("============================================");
        System.out.println("  Tamamlandi!");
        System.out.println("  Dosya: " + outFile.getAbsolutePath());
        System.out.printf("  Toplam: %d satir%n", rows.size());
        System.out.printf("  SAFE:         %d ornek%n", safeCount);
        System.out.printf("  FOG_SLIPPERY: %d ornek%n", fogCount);
        System.out.printf("  ROCK_FALL:    %d ornek%n", rockCount);
        System.out.println();
        System.out.println("  Beklenen dogruluk (10-fold CV):");
        System.out.println("  J48 Karar Agaci: ~88-93%");
        System.out.println("  Naive Bayes    : ~82-88%");
        System.out.println("  (Gercekci konfuzyon matrisi gorulecek!)");
        System.out.println("============================================");
    }

    private static double gauss(double mean, double std, double min, double max) {
        return Math.max(min, Math.min(max, mean + RNG.nextGaussian() * std));
    }

    private static String[] row(double s, double b, double j, int p, String lbl) {
        return new String[]{
            String.format(Locale.US, "%.2f", s),
            String.format(Locale.US, "%.2f", b),
            String.format(Locale.US, "%.2f", j),
            String.valueOf(Math.max(1, p)),
            lbl
        };
    }
}
