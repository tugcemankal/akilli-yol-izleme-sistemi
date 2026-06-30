package main.network;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CSV Logger — Paket pencerelerini otomatik etiketleyerek dataset.csv'ye kaydeder.
 *
 * Her 5 saniyelik pencere için şu özellikler kaydedilir:
 *   window_id, timestamp, avg_size, bytes_per_sec, jitter_ms, packet_count, label
 *
 * Etiket (label) hava koşullarından otomatik atanır:
 *   SAFE         → λ ≤ 1.5  (güneşli / parçalı bulutlu)
 *   FOG_SLIPPERY → λ = 2.0-3.5 (sisli / yağmurlu)
 *   ROCK_FALL    → λ ≥ 3.5  (fırtınalı / anlık burst)
 */
public class CsvLogger {

    private static final String CSV_FILE = "dataset.csv";
    private static final String HEADER   =
        "window_id,timestamp,avg_size,bytes_per_sec,jitter_ms,packet_count,label\n";

    private final PrintWriter writer;
    private int windowId = 0;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Pencere içinde biriken paket verileri
    private final CopyOnWriteArrayList<Integer> windowPacketSizes = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Long>    windowArrivalTimes = new CopyOnWriteArrayList<>();

    public CsvLogger() throws IOException {
        // Dosya yoksa başlığı yaz, varsa üzerine devam et
        boolean exists = Files.exists(Paths.get(CSV_FILE));
        FileWriter fw = new FileWriter(CSV_FILE, true); // append = true
        writer = new PrintWriter(new BufferedWriter(fw));
        if (!exists) {
            writer.print(HEADER);
            writer.flush();
        }
        System.out.println("[CsvLogger] Dataset dosyası: " + 
            Paths.get(CSV_FILE).toAbsolutePath());
    }

    /**
     * Paketi pencere tamponuna ekle
     */
    public synchronized void addPacket(int sizeBytes) {
        windowPacketSizes.add(sizeBytes);
        windowArrivalTimes.add(System.currentTimeMillis());
    }

    /**
     * 5 saniyelik pencereyi kapat, hesapla ve CSV'ye yaz.
     * SimulationController'dan her 5 saniyede bir çağrılır.
     *
     * @param lambda  Mevcut Poisson lambda değeri (etiket tespiti için)
     */
    public synchronized void flushWindow(double lambda) {
        if (windowPacketSizes.isEmpty()) return;

        List<Integer> sizes  = new ArrayList<>(windowPacketSizes);
        List<Long>    times  = new ArrayList<>(windowArrivalTimes);
        windowPacketSizes.clear();
        windowArrivalTimes.clear();

        // --- Özellik hesaplama ---

        // 1. Ortalama paket boyutu (byte)
        double avgSize = sizes.stream().mapToInt(i -> i).average().orElse(0);

        // 2. Saniyedeki toplam bayt (5 saniyelik pencere)
        int totalBytes = sizes.stream().mapToInt(i -> i).sum();
        double bytesPerSec = totalBytes / 5.0;

        // 3. Jitter (ms) — paket geliş sürelerindeki standart sapma
        double jitterMs = 0.0;
        if (times.size() > 1) {
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < times.size(); i++) {
                intervals.add(times.get(i) - times.get(i - 1));
            }
            double meanInterval = intervals.stream().mapToLong(l -> l).average().orElse(0);
            double variance = intervals.stream()
                .mapToDouble(l -> Math.pow(l - meanInterval, 2))
                .average().orElse(0);
            jitterMs = Math.sqrt(variance);
        }

        // --- Etiket ---
        String label = classifyByLambda(lambda, bytesPerSec, jitterMs);

        // --- CSV satırı yaz ---
        windowId++;
        String timestamp = sdf.format(new Date());
        String line = String.format(Locale.US,
            "%d,%s,%.2f,%.2f,%.2f,%d,%s\n",
            windowId, timestamp, avgSize, bytesPerSec, jitterMs, sizes.size(), label
        );
        writer.print(line);
        writer.flush();
    }

    /**
     * Lambda + ağ istatistiklerine göre etiket belirle.
     *
     * SAFE         → sakin trafik, küçük paketler
     * FOG_SLIPPERY → orta yoğunluk, sürekli yük
     * ROCK_FALL    → yüksek burst, yüksek jitter
     */
    private String classifyByLambda(double lambda, double bytesPerSec, double jitterMs) {
        if (lambda >= 3.5 || (bytesPerSec > 8000 && jitterMs > 60)) {
            return "ROCK_FALL";
        } else if (lambda >= 2.0 || (bytesPerSec > 2000 && jitterMs > 20)) {
            return "FOG_SLIPPERY";
        } else {
            return "SAFE";
        }
    }

    /**
     * Logger'ı kapat (uygulama kapanırken çağrılır)
     */
    public void close() {
        if (writer != null) writer.close();
        System.out.println("[CsvLogger] Dataset kaydedildi: " + CSV_FILE);
    }

    public int getWindowId() { return windowId; }
}
