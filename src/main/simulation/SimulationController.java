package main.simulation;

import main.model.DroneUnit;
import main.model.RiskEvent;
import main.model.SensorNode;
import main.network.CsvLogger;
import main.network.PacketSender;
import main.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ana Simülasyon Kontrolcüsü
 * 
 * Poisson event generation, enerji modeli, risk hesaplama,
 * drone dispatch ve hava durumu güncellemelerini yönetir.
 */
public class SimulationController {

    // ============================================================
    // CALLBACK İNTERFACELER
    // ============================================================
    public interface SimulationListener {
        void onNodeActivated(SensorNode node);
        void onEventGenerated(RiskEvent event, SensorNode node);
        void onRiskUpdated(SensorNode node);
        void onWeatherUpdated();
        void onDroneDispatched(DroneUnit drone, SensorNode target);
        void onSimulationTick(long tickCount);
        void onLoraMessage(String message);
    }

    // ============================================================
    // ALAN TANIMLARI
    // ============================================================
    private final List<SensorNode> nodes;
    private final List<DroneUnit> drones;
    private final PoissonEngine poissonEngine;
    private final WeatherService weatherService;
    private final RiskPredictor riskPredictor;
    private final Random random;

    // ── Ağ katmanı (PC 2'ye paket gönderimi + CSV kayıt) ──
    private PacketSender packetSender;
    private CsvLogger    csvLogger;
    private String       pc2IpAddress = PacketSender.DEFAULT_IP;
    private boolean      networkEnabled = false;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> mainTick;
    private ScheduledFuture<?> weatherTick;
    private ScheduledFuture<?> nodeActivationTick;
    private ScheduledFuture<?> droneTick;

    private final List<SimulationListener> listeners;
    private final CopyOnWriteArrayList<RiskEvent> globalEventLog;

    private boolean running = false;
    private long tickCount = 0;
    private long startTime = 0;

    // Node aktifleşme sırası (Poisson'a göre random)
    private final List<Integer> activationQueue;
    private int nextActivationIndex = 0;
    private static final int NODE_ACTIVATION_INTERVAL_MS = 1500; // 1.5 saniyede bir yeni node

    // İstatistikler
    private int totalEventsGlobal = 0;
    private int criticalAlertsCount = 0;
    private int dronesDispatched = 0;

    public SimulationController() {
        this.nodes = new ArrayList<>();
        this.drones = new ArrayList<>();
        this.poissonEngine = new PoissonEngine(Constants.LAMBDA_SUNNY);
        this.weatherService = new WeatherService();
        this.riskPredictor = new RiskPredictor(this);
        this.random = new Random();
        this.listeners = new CopyOnWriteArrayList<>();
        this.globalEventLog = new CopyOnWriteArrayList<>();
        this.activationQueue = new ArrayList<>();

        initializeNodes();
        initializeDrones();
        buildActivationQueue();
    }

    /**
     * Node'ları başlat
     */
    private void initializeNodes() {
        for (int i = 0; i < Constants.NODE_COORDINATES.length; i++) {
            double lat  = Constants.NODE_COORDINATES[i][0];
            double lon  = Constants.NODE_COORDINATES[i][1];
            double slope = Constants.NODE_COORDINATES[i][2];
            String type = Constants.NODE_TYPES[i];
            SensorNode node = new SensorNode(i, lat, lon, type, slope);
            nodes.add(node);
        }
    }

    /**
     * Drone'ları başlat
     */
    private void initializeDrones() {
        for (int i = 0; i < Constants.NUM_DRONES; i++) {
            drones.add(new DroneUnit(i, i)); // Her drone kendi üssünde
        }
    }

    /**
     * Poisson'a göre rastgele aktifleşme sırası oluştur
     */
    private void buildActivationQueue() {
        activationQueue.clear();
        // Tüm node indekslerini listeye ekle
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) indices.add(i);

        // Fisher-Yates shuffle
        for (int i = indices.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = indices.get(i);
            indices.set(i, indices.get(j));
            indices.set(j, tmp);
        }
        activationQueue.addAll(indices);
    }

    // ============================================================
    // SİMÜLASYON BAŞLATMA / DURDURMA
    // ============================================================

    public void start() {
        if (running) return;
        running = true;
        startTime = System.currentTimeMillis();
        tickCount = 0;

        scheduler = Executors.newScheduledThreadPool(4);

        // 1. İlk hava durumunu çek
        scheduler.execute(() -> {
            weatherService.fetchWeather();
            updateDynamicLambda();
            notifyWeatherUpdated();
        });

        // 2. Node aktifleşme: her 1.5 saniyede bir yeni node
        nodeActivationTick = scheduler.scheduleAtFixedRate(
            this::activateNextNode,
            1000, NODE_ACTIVATION_INTERVAL_MS, TimeUnit.MILLISECONDS
        );

        // 3. Ana tick: her 3 saniyede Poisson + risk + enerji
        mainTick = scheduler.scheduleAtFixedRate(
            this::mainSimulationTick,
            2000, Constants.EVENT_GENERATION_INTERVAL_MS, TimeUnit.MILLISECONDS
        );

        // 4. Hava güncellemesi: her 30 saniyede
        weatherTick = scheduler.scheduleAtFixedRate(
            this::weatherUpdateTick,
            30000, Constants.WEATHER_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS
        );

        // 5. Drone tick: her 500ms
        droneTick = scheduler.scheduleAtFixedRate(
            this::droneTick,
            2000, 500, TimeUnit.MILLISECONDS
        );

        // 6. Ağ katmanını başlat (PC 2'ye paket gönderimi + CSV kayıt)
        startNetworkLayer();
    }

    /**
     * Ağ katmanını (PacketSender + CsvLogger) başlatır.
     * PC 2'nin IP adresi daha önce setPc2IpAddress() ile ayarlanmış olmalıdır.
     */
    private void startNetworkLayer() {
        try {
            csvLogger    = new CsvLogger();
            packetSender = new PacketSender(pc2IpAddress, PacketSender.DEFAULT_PORT, csvLogger);
            packetSender.start(poissonEngine.getLambda());
            networkEnabled = true;
            System.out.println("[Network] Paket gönderimi başlatıldı → " + pc2IpAddress);
        } catch (IOException e) {
            System.err.println("[Network] CSV Logger başlatılamadı: " + e.getMessage());
            networkEnabled = false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (mainTick != null) mainTick.cancel(false);
        if (weatherTick != null) weatherTick.cancel(false);
        if (nodeActivationTick != null) nodeActivationTick.cancel(false);
        if (droneTick != null) droneTick.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        // Ağ katmanını durdur
        if (packetSender != null) packetSender.stop();
        if (csvLogger    != null) csvLogger.close();
    }

    public void reset() {
        stop();
        tickCount = 0;
        totalEventsGlobal = 0;
        criticalAlertsCount = 0;
        dronesDispatched = 0;
        globalEventLog.clear();
        nextActivationIndex = 0;

        for (SensorNode node : nodes) {
            // Node sıfırla
            nodes.set(nodes.indexOf(node), new SensorNode(
                node.getId(),
                node.getLatitude(),
                node.getLongitude(),
                node.getType(),
                node.getSlopeAngle()
            ));
        }

        buildActivationQueue();
    }

    // ============================================================
    // TICK METODLARI
    // ============================================================

    /**
     * Sıradaki node'u aktifleştir
     */
    private void activateNextNode() {
        if (nextActivationIndex >= activationQueue.size()) {
            // Tüm node'lar aktif, durdur
            if (nodeActivationTick != null) {
                nodeActivationTick.cancel(false);
            }
            return;
        }

        int nodeIndex = activationQueue.get(nextActivationIndex++);
        SensorNode node = nodes.get(nodeIndex);
        node.activate();

        // İlk sensör verisi
        node.updateSensorData(
            weatherService.getTemperature(),
            weatherService.getHumidity(),
            weatherService.getPressure(),
            weatherService.getWindSpeed(),
            0.1 + random.nextDouble() * 0.2,  // hafif titreşim
            weatherService.getVisibility()
        );

        notifyNodeActivated(node);
    }

    /**
     * Ana simülasyon tick'i
     * - Harita bazlı Poisson event generation (Hoca yönergesi)
     * - Her sensör için Öklid mesafe kontrolü: d = √((x_olay-x_sensor)² + (y_olay-y_sensor)²)
     * - Algılama koşulu: d ≤ r  →  sensör kırmızıya döner
     * - Risk hesaplama, enerji tüketimi, LoRa transmission
     */
    private void mainSimulationTick() {
        tickCount++;
        double deltaSeconds = Constants.EVENT_GENERATION_INTERVAL_MS / 1000.0;
        boolean isSolarCharging = weatherService.isDaytime() &&
                                   weatherService.getSolarChargeFactor() > 0.1;

        // ================================================================
        // HARITA BAZLI POISSON OLAY ÜRETİMİ  (Hoca Yönergesi)
        // ================================================================
        // Tüm harita alanında rastgele (X,Y) konumunda olay üret.
        // Harita koordinat aralığı: X → [0, MAP_WIDTH], Y → [0, MAP_HEIGHT]
        final double MAP_WIDTH  = 100.0;
        final double MAP_HEIGHT = 100.0;

        // Matematiksel gerekçe — Lambda Ölçekleme:
        // Eski (düğüm bazlı) sistemde her N aktif node ayrı Poisson(λ) üretiyordu.
        // → Toplam beklenen olay/tick = N × λ
        // Harita bazlı sistemde bu toplamı korumak için effectiveLambda = N × λ kullanılır.
        // Böylece toplam olay sayısı istatistiksel olarak tutarlı kalır.
        long activeCount = nodes.stream().filter(SensorNode::isActive).count();
        double effectiveLambda = poissonEngine.getLambda() * Math.max(1, activeCount);

        List<PoissonEngine.MapEvent> mapEvents =
            poissonEngine.generateMapBasedEvents(MAP_WIDTH, MAP_HEIGHT, effectiveLambda);

        // Her aktif sensör için:
        //   1. Öklid mesafesiyle hangi harita olaylarının kapsama alanına girdiğini bul
        //   2. Algılanan olayları RiskEvent'e dönüştür
        for (SensorNode node : nodes) {
            if (!node.isActive()) continue;

            // Sensörün normalize harita konumunu hesapla
            // (koordinatlar gerçek GPS'ten [0,100] aralığına ölçeklenir)
            double sensorX = normalizeLon(node.getLongitude());
            double sensorY = normalizeLat(node.getLatitude());

            // Sensörün algılama yarıçapı r  (çap/alan oranına göre modellenir)
            // r, node tipine göre farklılaşır: CLIFF_EDGE daha geniş, TUNNEL daha dar
            double detectionRadius = getSensorRadius(node);

            // 1. Sensör verilerini güncelle
            double newVibration = calculateVibration(node);
            node.updateSensorData(
                weatherService.getTemperature(),
                weatherService.getHumidity(),
                weatherService.getPressure(),
                weatherService.getWindSpeed(),
                newVibration,
                weatherService.getVisibility()
            );

            // 2. Harita olaylarını Öklid mesafesiyle kontrol et
            //    d = √((x_olay - x_sensor)² + (y_olay - y_sensor)²)
            //    d ≤ r → olay algılandı
            List<RiskEvent> detectedEvents = new ArrayList<>();

            for (PoissonEngine.MapEvent mapEvt : mapEvents) {
                boolean detected = PoissonEngine.isEventDetectedBy(
                    mapEvt, sensorX, sensorY, detectionRadius
                );

                if (detected) {
                    // Algılanan olay → RiskEvent oluştur
                    RiskEvent.EventType eventType = selectEventTypeForNode(node);
                    double magnitude = 0.4 + random.nextDouble() * 0.6;
                    magnitude = Math.min(1.0, magnitude *
                        Math.max(0.5, mapEvt.lambda / Constants.LAMBDA_SUNNY));


                    RiskEvent event = new RiskEvent(
                        node.getId(),
                        node.getNodeId(),
                        node.getLocation(),
                        eventType,
                        magnitude,
                        mapEvt.lambda,
                        mapEvt.k,
                        mapEvt.probability,
                        weatherService.getTemperature() + (random.nextDouble() - 0.5) * 3,
                        weatherService.getHumidity(),
                        weatherService.getWindSpeed(),
                        node.getVibration(),
                        // Harita bazlı koordinatlar ve mesafe bilgisi
                        mapEvt.x, mapEvt.y,
                        mapEvt.lastDistance,
                        detectionRadius,
                        true   // detected = true (d ≤ r)
                    );

                    detectedEvents.add(event);
                    node.addEvent(event);
                }
            }

            boolean hasEvents = !detectedEvents.isEmpty();
            boolean isTransmitting = hasEvents || (tickCount % 20 == node.getId() % 20);

            // 3. Enerji tüketimi
            node.updateEnergy(deltaSeconds, true, isTransmitting,
                isSolarCharging && weatherService.getSolarChargeFactor() > 0);

            // 4. Risk hesapla
            RiskCalculator.calculateRisk(node, weatherService.getPrecipitation());

            // 5. LoRa transmission (periyodik veya event tetikli)
            if (isTransmitting) {
                int sf = calculateSpreadingFactor(node);
                node.recordLoraTransmission(sf);

                if (node.getLastLoraMessage() != null) {
                    String logMsg = node.getLastLoraMessage();
                    notifyLoraMessage(logMsg);
                }
            }

            // 6. Event bildirimleri
            for (RiskEvent event : detectedEvents) {
                globalEventLog.add(0, event);
                if (globalEventLog.size() > 500) globalEventLog.remove(globalEventLog.size() - 1);
                totalEventsGlobal++;
                notifyEventGenerated(event, node);
            }

            // Madde 2 — TTL: 30 saniyeden eski olayları temizle (Event Decay)
            // Simülasyon uzun çalıştığında tüm node'ların kırmızı kalmasını önler.
            node.clearOldEvents(30);

            // 7. Risk bildirimi
            notifyRiskUpdated(node);

            // 8. DANGER veya CRITICAL → drone dispatch + fiziksel aksiyon logu
            if (node.getRiskLevel() == SensorNode.RiskLevel.CRITICAL ||
                node.getRiskLevel() == SensorNode.RiskLevel.DANGER) {

                if (node.getRiskLevel() == SensorNode.RiskLevel.CRITICAL) {
                    criticalAlertsCount++;
                }

                dispatchDroneTo(node);

                // Madde 4 — Fiziksel müdahale logu (CANLI OLAY AKIŞI)
                String emoji  = node.getRiskLevel() == SensorNode.RiskLevel.CRITICAL ? "🔴" : "🟠";
                String seviye = node.getRiskLevel() == SensorNode.RiskLevel.CRITICAL ? "KRİTİK" : "TEHLİKE";
                String actionLog = String.format(
                    "%s [OTONOM AKSİYON] %s → %s! " +
                    "Keşif Drone'u %s bölgesine sevk edildi! " +
                    "Risk: %.0f%% | λ=%.2f",
                    emoji, node.getNodeId(), seviye,
                    node.getLocation(),
                    node.getOverallRisk() * 100,
                    poissonEngine.getLambda()
                );
                notifyLoraMessage(actionLog);
            }
        }

        notifySimulationTick(tickCount);
    }


    /**
     * Hava durumu tick'i
     */
    public void updateDynamicLambda() {
        double lam = 0.5; // Temel lambda
        WeatherService w = weatherService;

        // Yağış etkisi: her 1 mm/h yağış, lambdayı 1.5 artırır
        lam += w.getPrecipitation() * 1.5;

        // Rüzgar etkisi: rüzgar hızı 20 km/h üzerindeyse her 10 km/h rüzgar lambdayı 0.04 artırır
        if (w.getWindSpeed() > 20.0) {
            lam += (w.getWindSpeed() - 20.0) * 0.04;
        }

        // Nem etkisi: Nem %80 üzerindeyse lambdayı artırır
        if (w.getHumidity() > 80.0) {
            lam += (w.getHumidity() - 80.0) * 0.06;
        }

        // Görüş mesafesi etkisi: Görüş 10 km altındaysa lambdayı artırır
        if (w.getVisibility() < 10000.0) {
            lam += (10000.0 - w.getVisibility()) / 1000.0 * 0.3;
        }

        // Bulutluluk etkisi
        if (w.getCloudCover() > 70) {
            lam += (w.getCloudCover() - 70) * 0.01;
        }

        // Sınırla
        // Demo için lambda max 4.5: "her saniye kaya mı düşüyor?" sorusunu önler
        lam = Math.max(0.3, Math.min(4.5, lam));
        poissonEngine.setLambda(lam);
        // PacketSender'a yeni lambda'yı bildir
        if (packetSender != null && packetSender.isRunning()) {
            packetSender.updateLambda(lam);
        }
    }

    private void weatherUpdateTick() {
        weatherService.fetchWeather();
        updateDynamicLambda();
        riskPredictor.update();
        notifyWeatherUpdated();
    }

    /**
     * Drone güncelleme tick'i
     */
    private void droneTick() {
        double delta = 0.5; // 500ms
        for (DroneUnit drone : drones) {
            boolean arrived = drone.updatePosition(delta);
            if (arrived) {
                // Drone hedefe ulaştı - log
                String msg = String.format("[DRONE] %s → %s BÖLGEYE ULAŞTI. İnceleme başlıyor.",
                    drone.getDroneId(), drone.getTargetLocation());
                notifyLoraMessage(msg);
            }
            drone.tickInspection(delta);
        }
    }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    // ── Harita Normalizasyon Metodları ─────────────────────────────────────────
    // GPS koordinatlarını [0, 100] normalize harita uzayına çevirir.
    // Tüm node'lar bu [0,100]x[0,100] ızgara üzerinde konumlandırılır.
    // Öklid mesafesi bu normalize koordinatlar üzerinde hesaplanır.

    /** Boylam → [0, 100] normalize X koordinatı */
    private double normalizeLon(double lon) {
        final double LON_MIN = 31.95;  // Alanya batı sınırı
        final double LON_MAX = 34.05;  // Silifke doğu sınırı
        return (lon - LON_MIN) / (LON_MAX - LON_MIN) * 100.0;
    }

    /** Enlem → [0, 100] normalize Y koordinatı */
    private double normalizeLat(double lat) {
        final double LAT_MIN = 36.05;  // güney sınırı
        final double LAT_MAX = 36.60;  // kuzey sınırı
        return (lat - LAT_MIN) / (LAT_MAX - LAT_MIN) * 100.0;
    }

    /**
     * Sensörün algılama yarıçapını hesaplar.
     *
     * Hoca yönergesi: "Çap/alan oranı gibi matematiksel olarak modellenecek."
     *
     * Formül: r = √(A / π)  →  A sensörün kapsama alanı
     * Node tipine göre kapsama alanı:
     *   CLIFF_EDGE : geniş kapsama (uçurum, titreşim sensörü)
     *   TUNNEL     : dar kapsama (tünel içi, yönlendirilmiş)
     *   COASTAL    : orta kapsama (kıyı izleme)
     *
     * @param node Sensör node'u
     * @return Algılama yarıçapı r (normalize harita birimi)
     */
    private double getSensorRadius(SensorNode node) {
        // Kapsama alanı A (normalize birim²)
        double area;
        switch (node.getType()) {
            case "CLIFF_EDGE": area = 120.0; break;  // ~6.2 birim yarıçap
            case "TUNNEL":     area =  40.0; break;  // ~3.6 birim yarıçap
            default:           area =  78.5; break;  // ~5.0 birim yarıçap (π×5²)
        }
        // r = √(A / π)  — çap/alan oranından türetilen yarıçap
        return Math.sqrt(area / Math.PI);
    }

    /**
     * Node tipine ve hava koşuluna göre olay türü seç.
     * (PoissonEngine.selectEventType'ın kontrolcü tarafına taşınmış hali)
     */
    private RiskEvent.EventType selectEventTypeForNode(SensorNode node) {
        double r = random.nextDouble();
        double humidity    = node.getHumidity();
        double temperature = node.getTemperature();
        double windSpeed   = node.getWindSpeed();
        String nodeType    = node.getType();

        if (humidity > 85 && r < 0.35)                              return RiskEvent.EventType.FOG_FORMATION;
        if (humidity > 75 && temperature < 10 && r < 0.30)         return RiskEvent.EventType.ROAD_SLIPPERY;
        if (nodeType.equals("CLIFF_EDGE") && r < 0.40)             return RiskEvent.EventType.ROCK_VIBRATION;
        if (humidity > 80 && nodeType.equals("CLIFF_EDGE") && r < 0.20) {
            double vibNoise = nodeType.equals("CLIFF_EDGE") ? 0.6 : nodeType.equals("TUNNEL") ? 0.3 : 0.15;
            return RiskEvent.EventType.MICRO_LANDSLIDE;
        }
        if (windSpeed > 50 && r < 0.25)                            return RiskEvent.EventType.STRONG_WIND;
        if (nodeType.equals("TUNNEL") && r < 0.20)                 return RiskEvent.EventType.VISIBILITY_LOW;

        RiskEvent.EventType[] types = RiskEvent.EventType.values();
        return types[(int)(r * types.length) % types.length];
    }

    /**
     * Node tipine ve olay durumuna göre titreşim hesapla
     */
    private double calculateVibration(SensorNode node) {
        double base = 0.0;
        // Rüzgar etkisi
        base += weatherService.getWindSpeed() / 200.0;
        // Yağış etkisi
        base += weatherService.getPrecipitation() / 50.0;
        // Uçurum kenarı doğal titreşim
        if (node.getType().equals("CLIFF_EDGE")) {
            base += 0.2 + Math.random() * 0.3;
        }
        // Rastgele gürültü
        base += (Math.random() - 0.5) * 0.1;
        return Math.max(0, base);
    }


    /**
     * Mesafeye ve RSSI'ya göre LoRa Spreading Factor seç
     */
    private int calculateSpreadingFactor(SensorNode node) {
        // En yakın gateway'e mesafeye göre SF seç
        double[] gw = Constants.GATEWAY_COORDINATES[node.getNearestGateway()];
        double dlat = node.getLatitude() - gw[0];
        double dlon = node.getLongitude() - gw[1];
        double distKm = Math.sqrt(dlat * dlat + dlon * dlon) * 111.0;

        // SF7 < 5km, SF9 < 10km, SF12 > 10km
        if (distKm < 5) return 7;
        else if (distKm < 10) return 9;
        else return 12;
    }

    /**
     * En yakın boştaki drone'u kritik node'a gönder
     */
    private void dispatchDroneTo(SensorNode node) {
        // Aynı node için zaten bir drone gönderilmiş mi?
        for (DroneUnit drone : drones) {
            if (!drone.isAvailable() && drone.getTargetNodeId() == node.getId()) {
                return;
            }
        }

        // En yakın boştaki drone'u bul
        DroneUnit nearest = null;
        double minDist = Double.MAX_VALUE;

        for (DroneUnit drone : drones) {
            if (!drone.isAvailable()) continue;
            double dlat = drone.getCurrentLat() - node.getLatitude();
            double dlon = drone.getCurrentLon() - node.getLongitude();
            double dist = Math.sqrt(dlat * dlat + dlon * dlon);
            if (dist < minDist) {
                minDist = dist;
                nearest = drone;
            }
        }

        if (nearest != null) {
            nearest.dispatchTo(node);
            dronesDispatched++;
            notifyDroneDispatched(nearest, node);
            notifyLoraMessage(String.format(
                "[DRONE] %s → %s KONUMUNA ÇIKARILDI. Risk: %s",
                nearest.getDroneId(), node.getLocation(), node.getRiskLevel().label
            ));
        }
    }

    // ============================================================
    // LİSTENER METODLARI
    // ============================================================

    public void addListener(SimulationListener listener) {
        listeners.add(listener);
    }

    private void notifyNodeActivated(SensorNode node) {
        for (SimulationListener l : listeners) {
            try { l.onNodeActivated(node); } catch (Exception e) {}
        }
    }

    private void notifyEventGenerated(RiskEvent event, SensorNode node) {
        for (SimulationListener l : listeners) {
            try { l.onEventGenerated(event, node); } catch (Exception e) {}
        }
    }

    private void notifyRiskUpdated(SensorNode node) {
        for (SimulationListener l : listeners) {
            try { l.onRiskUpdated(node); } catch (Exception e) {}
        }
    }

    private void notifyWeatherUpdated() {
        for (SimulationListener l : listeners) {
            try { l.onWeatherUpdated(); } catch (Exception e) {}
        }
    }

    private void notifyDroneDispatched(DroneUnit drone, SensorNode target) {
        for (SimulationListener l : listeners) {
            try { l.onDroneDispatched(drone, target); } catch (Exception e) {}
        }
    }

    private void notifySimulationTick(long tick) {
        for (SimulationListener l : listeners) {
            try { l.onSimulationTick(tick); } catch (Exception e) {}
        }
    }

    private void notifyLoraMessage(String message) {
        for (SimulationListener l : listeners) {
            try { l.onLoraMessage(message); } catch (Exception e) {}
        }
    }

    // ============================================================
    // GETTERS
    // ============================================================
    public List<SensorNode> getNodes() { return nodes; }
    public List<DroneUnit> getDrones() { return drones; }
    public PoissonEngine getPoissonEngine() { return poissonEngine; }
    public WeatherService getWeatherService() { return weatherService; }
    public RiskPredictor getRiskPredictor() { return riskPredictor; }
    public boolean isRunning() { return running; }

    // Ağ katmanı getterları
    public PacketSender getPacketSender()   { return packetSender; }
    public CsvLogger    getCsvLogger()       { return csvLogger; }
    public boolean      isNetworkEnabled()   { return networkEnabled; }
    public String       getPc2IpAddress()    { return pc2IpAddress; }

    /**
     * PC 2'nin IP adresini ayarla (simülasyon başlamadan önce çağrılmalı)
     */
    public void setPc2IpAddress(String ip)  { this.pc2IpAddress = ip; }
    public long getTickCount() { return tickCount; }
    public long getElapsedSeconds() {
        return running ? (System.currentTimeMillis() - startTime) / 1000 : 0;
    }
    public int getTotalEventsGlobal() { return totalEventsGlobal; }
    public int getCriticalAlertsCount() { return criticalAlertsCount; }
    public int getDronesDispatched() { return dronesDispatched; }
    public CopyOnWriteArrayList<RiskEvent> getGlobalEventLog() { return globalEventLog; }
    public int getActiveNodeCount() {
        return (int) nodes.stream().filter(SensorNode::isActive).count();
    }
    public int getCriticalNodeCount() {
        return (int) nodes.stream()
            .filter(n -> n.isActive() && n.getRiskLevel() == SensorNode.RiskLevel.CRITICAL)
            .count();
    }
}
