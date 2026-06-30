package main.model;

import main.util.Constants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WSN Node Modeli - Pycom FiPy tabanlı kablosuz sensör düğümü
 * Donanım: Pycom FiPy + Bosch BME280 + InvenSense MPU-6050
 */
public class SensorNode {

    public enum NodeState {
        OFFLINE,    // Henüz aktifleşmedi
        SLEEPING,   // Uyku modu (düşük güç)
        SENSING,    // Sensör okuma yapıyor
        TRANSMITTING // LoRa ile veri gönderiyor
    }

    public enum RiskLevel {
        SAFE(0, "SAFE", "#00E676"),
        CAUTION(1, "CAUTION", "#FFEB3B"),
        DANGER(2, "DANGER", "#FF9800"),
        CRITICAL(3, "CRITICAL", "#F44336");

        public final int level;
        public final String label;
        public final String color;

        RiskLevel(int level, String label, String color) {
            this.level = level;
            this.label = label;
            this.color = color;
        }
    }

    // Kimlik bilgileri
    private final int id;
    private final String nodeId;
    private final double latitude;
    private final double longitude;
    private final String type;          // COASTAL, CLIFF_EDGE, TUNNEL
    private final String location;      // İnsan okunabilir konum adı
    private final int nearestGateway;   // En yakın LoRa gateway indeksi
    private final double slopeAngle;    // Eğim açısı (derece)

    // Durum bilgileri
    private NodeState state;
    private boolean active;             // Simülasyonda aktif mi
    private LocalDateTime activatedAt;
    private LocalDateTime lastEventTime;

    // Sensör okumaları (BME280 + MPU6050)
    private double temperature;         // °C
    private double humidity;            // % RH
    private double pressure;            // hPa
    private double windSpeed;           // km/h
    private double vibration;           // g (ivme)
    private double visibility;          // metre

    // Risk hesaplamaları
    private double rockRisk;            // 0.0 - 1.0
    private double fogRisk;             // 0.0 - 1.0
    private double slipRisk;            // 0.0 - 1.0
    private double overallRisk;         // 0.0 - 1.0
    private RiskLevel riskLevel;

    // Enerji modeli (FiPy datasheet)
    private double batteryPercent;      // 0-100 %
    private double batteryJoules;       // Joule cinsinden mevcut enerji
    private double totalConsumedMah;    // mAh cinsinden toplam tüketim
    private double totalConsumedJoules; // Joule cinsinden toplam tüketim

    // LoRa iletişim
    private int loraPacketCount;
    private double loraRssi;            // dBm
    private int loraSf;                 // Spreading Factor
    private String lastLoraMessage;

    // Olaylar
    private List<RiskEvent> eventHistory;
    private RiskEvent lastEvent;
    private int totalEventCount;

    public SensorNode(int id, double latitude, double longitude, String type, double slopeAngle) {
        this.id = id;
        this.nodeId = String.format("NODE-%03d", id + 1);
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.slopeAngle = slopeAngle;
        this.location = generateLocationName(id);
        this.nearestGateway = findNearestGateway(latitude, longitude);

        // Başlangıç durumu: OFFLINE
        this.state = NodeState.OFFLINE;
        this.active = false;

        // Enerji: gerçekçi başlangıç — tüm node'lar tam dolu değil
        // 85-100% arası rassal başlangıç (saha dağıtımında önceden birazcık tüketim)
        double initialPct = 0.85 + Math.random() * 0.15;
        this.batteryJoules  = Constants.FIPY_BATTERY_JOULES * initialPct;
        this.batteryPercent = initialPct * 100.0;
        this.totalConsumedMah    = 0.0;
        this.totalConsumedJoules = 0.0;

        // Sensör başlangıç değerleri
        this.temperature = 20.0;
        this.humidity = 60.0;
        this.pressure = 1013.0;
        this.windSpeed = 5.0;
        this.vibration = 0.0;
        this.visibility = 10000.0;

        // Risk başlangıç
        this.rockRisk = 0.0;
        this.fogRisk = 0.0;
        this.slipRisk = 0.0;
        this.overallRisk = 0.0;
        this.riskLevel = RiskLevel.SAFE;

        // LoRa
        this.loraPacketCount = 0;
        this.loraRssi = -85.0 - Math.random() * 40; // -85 ile -125 dBm arası
        this.loraSf = Constants.LORA_SF_DEFAULT;

        // Olaylar
        this.eventHistory = new ArrayList<>();
        this.totalEventCount = 0;
    }

    /**
     * Node'u aktifleştir (Poisson tetiklemesi)
     */
    public void activate() {
        if (!active) {
            this.active = true;
            this.state = NodeState.SLEEPING;
            this.activatedAt = LocalDateTime.now();
        }
    }

    /**
     * Enerji tüketimini simüle et
     * @param deltaSeconds geçen süre (saniye)
     * @param isSensing sensör okuma yapıyor mu
     * @param isTransmitting LoRa ile gönderiyor mu
     * @param isSolarCharging güneş şarjı var mı
     */
    public void updateEnergy(double deltaSeconds, boolean isSensing,
                              boolean isTransmitting, boolean isSolarCharging) {
        if (!active) return;

        // ── Madde 3: Ölü Düğüm (Dead Node) Paradoksu Koruması ─────────────────
        // Gerçek dünyada pili biten cihaz susar; simülasyonda da aynı davranış.
        if (this.batteryJoules <= 0) {
            this.batteryJoules  = 0;
            this.batteryPercent = 0;
            this.active         = false;
            this.state          = NodeState.OFFLINE;
            this.riskLevel      = RiskLevel.SAFE;   // Ölü node risk üretemez
            return;
        }


        double currentMA = 0.0;

        // FiPy tüketimi
        if (isTransmitting) {
            currentMA += Constants.FIPY_LORA_TX_MA;

            this.state = NodeState.TRANSMITTING;
        } else if (isSensing) {
            currentMA += Constants.FIPY_ACTIVE_CPU_MA;
            this.state = NodeState.SENSING;
        } else {
            currentMA += Constants.FIPY_SLEEP_CURRENT_MA;
            this.state = NodeState.SLEEPING;
        }

        // BME280 tüketimi (µA → mA)
        if (isSensing) {
            currentMA += Constants.BME280_NORMAL_MODE_UA / 1000.0;
        } else {
            currentMA += Constants.BME280_SLEEP_MODE_UA / 1000.0;
        }

        // MPU-6050 tüketimi
        currentMA += Constants.MPU6050_NORMAL_MA;

        // Harcanan enerji (mAh)
        double consumedMah = currentMA * (deltaSeconds / 3600.0);

        // Solar şarj
        if (isSolarCharging) {
            // Regülatör verimliliği %85
            double chargeMah = Constants.SOLAR_PANEL_CURRENT_MA * 0.85 * (deltaSeconds / 3600.0);
            consumedMah -= chargeMah;
        }

        // Joule cinsinden enerji harcaması
        double consumedJoules = consumedMah * Constants.FIPY_VOLTAGE_V * 3.6;

        // ── Asimetrik Ekstra Tüketim ──────────────────────────────────────────────
        // 1) Eğim açısı etkisi: dik arazide MPU-6050 daha sık örnekleme yapar
        double slopeFactor = Math.max(0, (slopeAngle - 10.0) / 80.0); // 0.0 – 0.25
        consumedJoules += slopeFactor * 0.004 * deltaSeconds; // maks ~0.001 J/s

        // 2) Kritik risk durumunda acil paket patlaması: daha sık TX
        if (riskLevel == RiskLevel.CRITICAL) {
            consumedJoules += 0.012 * deltaSeconds; // ~0.012 J/s ekstra
        } else if (riskLevel == RiskLevel.DANGER) {
            consumedJoules += 0.005 * deltaSeconds;
        }

        // 3) Random gürültü: gerçek sensörler biraz varyasyon gösterir
        consumedJoules += (Math.random() - 0.5) * 0.002;

        // Güncelle
        this.totalConsumedMah    += Math.max(0, consumedMah);
        this.totalConsumedJoules += Math.max(0, consumedJoules);
        this.batteryJoules       = Math.max(0, batteryJoules - consumedJoules);
        this.batteryPercent      = (batteryJoules / Constants.FIPY_BATTERY_JOULES) * 100.0;
    }

    /**
     * Bir LoRa paketi gönderildi
     */
    public void recordLoraTransmission(int spreadingFactor) {
        this.loraPacketCount++;
        this.loraSf = spreadingFactor;
        // SF arttıkça menzil artar ama hız düşer; RSSI simülasyonu
        this.loraRssi = -85.0 - (spreadingFactor - 7) * 5 - Math.random() * 10;
        this.lastLoraMessage = buildLoraMessage();
    }

    private String buildLoraMessage() {
        return String.format(
            "%s → %s | SF%d | %.0fdBm | T:%.1f°C | H:%.0f%% | P:%.0fhPa | Risk:%s",
            nodeId,
            Constants.GATEWAY_NAMES[nearestGateway],
            loraSf,
            loraRssi,
            temperature,
            humidity,
            pressure,
            riskLevel.label
        );
    }

    /**
     * Bir olay ekle
     */
    public void addEvent(RiskEvent event) {
        this.lastEvent = event;
        this.lastEventTime = LocalDateTime.now();
        this.totalEventCount++;
        if (eventHistory.size() > 50) {
            eventHistory.remove(0);
        }
        eventHistory.add(event);
    }

    /**
     * Madde 2 — Olay Zaman Aşımı / TTL (Time-to-Live)
     *
     * Erken uyarı sistemlerinde bir olayın etkisi zamanla azalır.
     * Bu metod, üzerinden EVENT_TTL_SECONDS saniye geçen olayları
     * listeden düşürür ve risk seviyesini tekrar hesaplar.
     *
     * Böylece simülasyon 10 dakika açık kalsa bile tüm düğümler
     * sonsuza kadar kırmızı kalmaz — iyileşme döngüsü çalışır.
     *
     * @param ttlSeconds Olayların yaşam süresi (saniye)
     */
    public void clearOldEvents(int ttlSeconds) {
        if (eventHistory.isEmpty()) return;

        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(ttlSeconds);
        boolean removed = eventHistory.removeIf(e -> e.getTimestamp().isBefore(cutoff));

        if (removed) {
            // Risk seviyesini sıfırla — RiskCalculator bir sonraki
            // tick'te zaten günceller, bu sadece anlık iyileşmeyi tetikler
            if (eventHistory.isEmpty()) {
                this.overallRisk = Math.max(0, this.overallRisk - 0.15);
                if (this.overallRisk < Constants.RISK_SAFE) {
                    this.riskLevel = RiskLevel.SAFE;
                } else if (this.overallRisk < Constants.RISK_CAUTION) {
                    this.riskLevel = RiskLevel.CAUTION;
                } else if (this.overallRisk < Constants.RISK_DANGER) {
                    this.riskLevel = RiskLevel.DANGER;
                }
            }
            // Son olay zamanını güncelle
            if (!eventHistory.isEmpty()) {
                this.lastEvent = eventHistory.get(eventHistory.size() - 1);
            } else {
                this.lastEvent = null;
            }
        }
    }

    /**
     * Sensör verilerini güncelle
     */
    public void updateSensorData(double temperature, double humidity, double pressure,
                                  double windSpeed, double vibration, double visibility) {
        this.temperature = temperature + (Math.random() - 0.5) * 2.0; // ±1°C gürültü
        this.humidity = Math.max(0, Math.min(100,
            humidity + (Math.random() - 0.5) * 5.0));                 // ±2.5% gürültü
        this.pressure = pressure + (Math.random() - 0.5) * 2.0;       // ±1 hPa gürültü
        this.windSpeed = Math.max(0,
            windSpeed + (Math.random() - 0.5) * 3.0);                 // ±1.5 km/h gürültü
        // CLIFF_EDGE: yüksek titreşim gürültüsü → rockRisk artışı → drone dispatch tetiklenir
        double vibNoise = type.equals("CLIFF_EDGE") ? 0.65
                        : type.equals("TUNNEL")     ? 0.35 : 0.15;
        this.vibration = Math.max(0, vibration + Math.random() * vibNoise);

        this.visibility = Math.max(100, visibility + (Math.random() - 0.5) * 200);
    }

    /**
     * Risk seviyelerini güncelle
     */
    public void updateRiskLevels(double rockRisk, double fogRisk, double slipRisk) {
        this.rockRisk = Math.max(0, Math.min(1, rockRisk));
        this.fogRisk = Math.max(0, Math.min(1, fogRisk));
        this.slipRisk = Math.max(0, Math.min(1, slipRisk));
        this.overallRisk = Math.max(rockRisk, Math.max(fogRisk, slipRisk));

        if (overallRisk < Constants.RISK_SAFE) {
            this.riskLevel = RiskLevel.SAFE;
        } else if (overallRisk < Constants.RISK_CAUTION) {
            this.riskLevel = RiskLevel.CAUTION;
        } else if (overallRisk < Constants.RISK_DANGER) {
            this.riskLevel = RiskLevel.DANGER;
        } else {
            this.riskLevel = RiskLevel.CRITICAL;
        }
    }

    // ============================================================
    // YARDIMCI METODLAR
    // ============================================================

    private String generateLocationName(int id) {
        String[] locations = {
            "Alanya Doğu", "Alanya-Gazipaşa", "Koru Burnu", "Kaledran", "Yüksek Viraj",
            "Kıyı Kesiği", "Gazipaşa Batı", "Gazipaşa", "Gazipaşa-Anamur Tünel", "Uçurum Noktası",
            "Kayalık Bölge", "Sarısu", "Karakabaklar", "Viraj Bölgesi", "Kıyı Yolu",
            "Ören", "Yenipazar", "Anamur Batı", "Anamur", "Anamur Tünel",
            "İskele Bölgesi", "Boğaz Geçidi", "Yüksek Kıyı", "Bozyazı Batı", "Bozyazı",
            "Bozyazı Doğu", "Hacıkırı", "Kıyı Kesiği-2", "Aydıncık Batı", "Aydıncık",
            "Aydıncık Doğu", "Kaya Kütlesi", "Uçurum Bölgesi", "Kritik Viraj", "Yeniyurt",
            "Limonlu", "Silifke Batı", "Göksu Deltası", "Silifke Yakını", "Taşucu",
            "Silifke", "Alanya Uçurum", "Kayalık Tepe", "Bozyazı Kritik", "Silifke Virajı"
        };
        return id < locations.length ? locations[id] : "Bilinmeyen Konum";
    }

    private int findNearestGateway(double lat, double lon) {
        int nearest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < Constants.GATEWAY_COORDINATES.length; i++) {
            double dlat = lat - Constants.GATEWAY_COORDINATES[i][0];
            double dlon = lon - Constants.GATEWAY_COORDINATES[i][1];
            double dist = Math.sqrt(dlat * dlat + dlon * dlon);
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    // ============================================================
    // GETTERS
    // ============================================================
    public int getId() { return id; }
    public String getNodeId() { return nodeId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getType() { return type; }
    public String getLocation() { return location; }
    public double getSlopeAngle() { return slopeAngle; }
    public NodeState getState() { return state; }
    public boolean isActive() { return active; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public LocalDateTime getLastEventTime() { return lastEventTime; }
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    public double getPressure() { return pressure; }
    public double getWindSpeed() { return windSpeed; }
    public double getVibration() { return vibration; }
    public double getVisibility() { return visibility; }
    public double getRockRisk() { return rockRisk; }
    public double getFogRisk() { return fogRisk; }
    public double getSlipRisk() { return slipRisk; }
    public double getOverallRisk() { return overallRisk; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public double getBatteryPercent() { return batteryPercent; }
    public double getBatteryJoules() { return batteryJoules; }
    public double getTotalConsumedMah() { return totalConsumedMah; }
    public int getLoraPacketCount() { return loraPacketCount; }
    public double getLoraRssi() { return loraRssi; }
    public int getLoraSf() { return loraSf; }
    public String getLastLoraMessage() { return lastLoraMessage; }
    public List<RiskEvent> getEventHistory() { return eventHistory; }
    public RiskEvent getLastEvent() { return lastEvent; }
    public int getTotalEventCount() { return totalEventCount; }
    public int getNearestGateway() { return nearestGateway; }

    // SETTERS
    public void setState(NodeState state) { this.state = state; }
    public void setBatteryPercent(double pct) { this.batteryPercent = pct; }
}
