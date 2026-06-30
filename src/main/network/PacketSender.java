package main.network;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Paket Gönderici — PC 1'den PC 2'ye UDP paketleri gönderir.
 *
 * Hava durumu / Poisson lambda'sına göre paket boyutu ve gönderim hızı değişir:
 *
 *   SAFE  (λ ≤ 1.5): ~60 byte, 60 saniyede bir  → Heartbeat (sakin trafik)
 *   FOG   (λ 2-3.5): ~300 byte, 5-10 sn'de bir  → Sürekli orta yük
 *   ROCK  (λ ≥ 3.5): 500-1500 byte, burst modu  → Ani, şiddetli patlama
 *
 * Her gönderilen paket CsvLogger'a bildirilir.
 *
 * Kullanım:
 *   PacketSender sender = new PacketSender("192.168.1.45", 9876, csvLogger);
 *   sender.start(lambda);          // simülasyon başında
 *   sender.updateLambda(newLam);   // hava değişince
 *   sender.stop();                 // simülasyon bitince
 */
public class PacketSender {

    // ── Ağ yapılandırması ──────────────────────────────────────────
    private final String targetIp;
    private final int    targetPort;

    // ── Bağımlılıklar ─────────────────────────────────────────────
    private final CsvLogger csvLogger;
    private final Random    random = new Random();

    // ── Çalışma durumu ────────────────────────────────────────────
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private volatile double currentLambda = 0.5;
    private volatile boolean running = false;

    // ── İstatistikler ─────────────────────────────────────────────
    private volatile long totalPacketsSent  = 0;
    private volatile long totalBytesSent    = 0;

    // ── Node başına ayrı zamanlanmış görev ────────────────────────
    // Her node kendi thread'inde çalışır, 45 node → 45 görev
    private static final int NODE_COUNT = 45;

    public PacketSender(String targetIp, int targetPort, CsvLogger csvLogger) {
        this.targetIp  = targetIp;
        this.targetPort = targetPort;
        this.csvLogger = csvLogger;
    }

    /**
     * Göndericiyi başlat
     */
    public void start(double initialLambda) {
        if (running) return;
        this.currentLambda = initialLambda;
        this.running = true;

        try {
            socket = new DatagramSocket();
            socket.setSendBufferSize(65535);
        } catch (SocketException e) {
            System.err.println("[PacketSender] Socket açılamadı: " + e.getMessage());
            return;
        }

        // Gönderim thread'leri — node sayısı kadar thread
        scheduler = Executors.newScheduledThreadPool(4);

        // Her node için ayrı gönderim görevi başlat
        for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
            final int id = nodeId;
            // Her node biraz farklı zamanda başlasın (dağıtık aktivasyon)
            long initialDelay = id * 100L; // 100ms arayla aktifleşme
            scheduler.scheduleAtFixedRate(
                () -> sendNodePacket(id),
                initialDelay,
                getIntervalMs(),   // başlangıç aralığı
                TimeUnit.MILLISECONDS
            );
        }

        // Her 5 sn'de bir pencereyi kapat (CsvLogger)
        scheduler.scheduleAtFixedRate(
            () -> csvLogger.flushWindow(currentLambda),
            5000, 5000, TimeUnit.MILLISECONDS
        );

        System.out.printf("[PacketSender] Başlatıldı → %s:%d | λ=%.2f%n",
            targetIp, targetPort, currentLambda);
    }

    /**
     * Lambda'yı güncelle (hava değişince SimulationController tarafından çağrılır)
     */
    public void updateLambda(double newLambda) {
        this.currentLambda = newLambda;
    }

    /**
     * Göndericiyi durdur
     */
    public void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) scheduler.shutdownNow();
        if (socket != null && !socket.isClosed()) socket.close();
        csvLogger.flushWindow(currentLambda); // son pencereyi kaydet
        System.out.printf("[PacketSender] Durduruldu. Toplam: %d paket, %d byte%n",
            totalPacketsSent, totalBytesSent);
    }

    // ─────────────────────────────────────────────────────────────
    // İÇ METODLAR
    // ─────────────────────────────────────────────────────────────

    /**
     * Tek bir node'un paketini gönder.
     * Paket içeriği: nodeId + timestamp + nodeType + risk etiketi (JSON benzeri string)
     */
    private void sendNodePacket(int nodeId) {
        if (!running || socket == null || socket.isClosed()) return;

        try {
            // Lambda'ya göre paket boyutu ve payload üret
            byte[] payload = buildPayload(nodeId, currentLambda);

            // UDP datagram gönder
            InetAddress address = InetAddress.getByName(targetIp);
            DatagramPacket packet = new DatagramPacket(
                payload, payload.length, address, targetPort
            );
            socket.send(packet);

            // CsvLogger'a bildir
            csvLogger.addPacket(payload.length);

            totalPacketsSent++;
            totalBytesSent += payload.length;

        } catch (IOException e) {
            // Ağ hatası — sessizce geç (PC 2 bağlı değilse paket düşer)
        }
    }

    /**
     * Lambda'ya göre gerçekçi paket payload'ı oluştur.
     *
     * SAFE:      60 byte civarı heartbeat  → küçük JSON
     * FOG:       200-400 byte              → sensör verisi
     * ROCK_FALL: 500-1500 byte burst       → büyük, gürültülü payload
     */
    private byte[] buildPayload(int nodeId, double lambda) {
        int targetSize;
        String label;

        if (lambda >= 3.5) {
            // ROCK_FALL: 500-1500 byte arası burst
            targetSize = 500 + random.nextInt(1001);
            label = "ROCK_FALL";
        } else if (lambda >= 2.0) {
            // FOG_SLIPPERY: 200-400 byte
            targetSize = 200 + random.nextInt(201);
            label = "FOG_SLIPPERY";
        } else {
            // SAFE: 55-70 byte heartbeat
            targetSize = 55 + random.nextInt(16);
            label = "SAFE";
        }

        // Temel JSON payload
        String base = String.format(
            "{\"node\":%d,\"ts\":%d,\"lam\":%.2f,\"lbl\":\"%s\",\"data\":\"",
            nodeId, System.currentTimeMillis(), lambda, label
        );

        // Hedef boyuta ulaşmak için dolgu ekle
        StringBuilder sb = new StringBuilder(base);
        while (sb.length() < targetSize - 2) {
            sb.append((char)('A' + random.nextInt(26)));
        }
        sb.append("\"}");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Mevcut lambda'ya göre gönderim aralığını hesapla (ms)
     * Bu değer sadece başlangıç aralığı olarak kullanılır.
     */
    private long getIntervalMs() {
        double lam = currentLambda;
        if (lam >= 3.5) return 200;      // burst: 200ms'de bir
        else if (lam >= 2.0) return 2000; // fog:   2sn'de bir
        else return 10000;                 // safe:  10sn'de bir (45 node × 10sn → yeterli trafik)
    }

    // ── Getterlar ─────────────────────────────────────────────────
    public long getTotalPacketsSent()  { return totalPacketsSent; }
    public long getTotalBytesSent()    { return totalBytesSent; }
    public double getCurrentLambda()   { return currentLambda; }
    public boolean isRunning()         { return running; }

    /**
     * Varsayılan IP ve port ile PacketSender oluşturur.
     * PC 2'nin IP'si build.bat argümanı veya sabit değer olarak girilir.
     */
    public static final int  DEFAULT_PORT = 9876;
    public static final String DEFAULT_IP = "192.168.1.100"; // PC 2'nin IP'si buraya!
}
