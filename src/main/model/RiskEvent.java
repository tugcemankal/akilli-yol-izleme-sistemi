package main.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Risk Olayı Modeli - Poisson ile üretilen olaylar
 */
public class RiskEvent {

    public enum EventType {
        ROCK_VIBRATION("Kaya Titreşimi", "🪨", "#FF6B6B"),
        FOG_FORMATION("Sis Yoğunlaşması", "🌫️", "#90CAF9"),
        ROAD_SLIPPERY("Kaygan Yol", "⚠️", "#FFCC02"),
        MICRO_LANDSLIDE("Mikro Heyelan", "⛰️", "#FF8A65"),
        STRONG_WIND("Şiddetli Rüzgar", "💨", "#80CBC4"),
        RAIN_SURGE("Ani Yağış", "🌧️", "#64B5F6"),
        VISIBILITY_LOW("Düşük Görüş", "👁️", "#CE93D8");

        public final String label;
        public final String emoji;
        public final String color;

        EventType(String label, String emoji, String color) {
            this.label = label;
            this.emoji = emoji;
            this.color = color;
        }
    }

    private final String eventId;
    private final int nodeId;
    private final String nodeLabel;
    private final String location;
    private final EventType type;
    private final double magnitude;         // Olay büyüklüğü (0-1)
    private final LocalDateTime timestamp;
    private final double poissonLambda;     // O anki λ değeri
    private final int poissonK;             // O anki k değeri
    private final double poissonProbability;// P(X=k) değeri

    // ── Harita bazlı olay koordinatları (Hoca yönergesi: "harita alanında rastgele X,Y") ──
    private final double eventX;            // Olayın haritadaki X koordinatı (0-100)
    private final double eventY;            // Olayın haritadaki Y koordinatı (0-100)
    private final double distanceToSensor;  // d = √((x_olay - x_sensor)² + (y_olay - y_sensor)²)
    private final double detectionRadius;   // Sensörün algılama yarıçapı r
    private final boolean detected;        // d ≤ r ise true (sensör algıladı)

    // Olayın sensör değerleri
    private final double temperature;
    private final double humidity;
    private final double windSpeed;
    private final double vibration;

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public RiskEvent(int nodeId, String nodeLabel, String location, EventType type,
                     double magnitude, double lambda, int k, double probability,
                     double temperature, double humidity, double windSpeed, double vibration) {
        this(nodeId, nodeLabel, location, type, magnitude, lambda, k, probability,
             temperature, humidity, windSpeed, vibration,
             -1, -1, 0, 0, true); // Geriye dönük uyumluluk: eski çağrılar algılanmış sayılır
    }

    /**
     * Harita bazlı olay constructor'ı
     * Poisson ile harita üzerinde rastgele üretilen ve Öklid mesafesiyle
     * algılama kontrolü yapılan olaylar için kullanılır.
     *
     * @param eventX          Olayın harita X koordinatı
     * @param eventY          Olayın harita Y koordinatı
     * @param distanceToSensor d = √((x_olay-x_sensor)² + (y_olay-y_sensor)²)
     * @param detectionRadius  Sensörün algılama yarıçapı r
     * @param detected         d ≤ r ise true
     */
    public RiskEvent(int nodeId, String nodeLabel, String location, EventType type,
                     double magnitude, double lambda, int k, double probability,
                     double temperature, double humidity, double windSpeed, double vibration,
                     double eventX, double eventY, double distanceToSensor,
                     double detectionRadius, boolean detected) {
        this.eventId = generateId();
        this.nodeId = nodeId;
        this.nodeLabel = nodeLabel;
        this.location = location;
        this.type = type;
        this.magnitude = magnitude;
        this.timestamp = LocalDateTime.now();
        this.poissonLambda = lambda;
        this.poissonK = k;
        this.poissonProbability = probability;
        this.temperature = temperature;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
        this.vibration = vibration;
        this.eventX = eventX;
        this.eventY = eventY;
        this.distanceToSensor = distanceToSensor;
        this.detectionRadius = detectionRadius;
        this.detected = detected;
    }

    private static String generateId() {
        return String.format("EVT-%06d", (int)(Math.random() * 999999));
    }

    public String toLoraLogString() {
        return String.format("[%s] %s | %s%s | λ=%.1f k=%d | T:%.1f°C H:%.0f%% W:%.1fkm/h",
            timestamp.format(FORMATTER),
            nodeLabel,
            type.emoji,
            type.label,
            poissonLambda,
            poissonK,
            temperature,
            humidity,
            windSpeed
        );
    }

    public String toCSVString() {
        return String.format("%s,%s,%s,%s,%.1f,%.0f,%.0f,%.2f,%.4f,%d,%.6f",
            timestamp,
            nodeLabel,
            location,
            type.label,
            temperature,
            humidity,
            windSpeed,
            vibration,
            poissonLambda,
            poissonK,
            poissonProbability
        );
    }

    // Getters
    public String getEventId() { return eventId; }
    public int getNodeId() { return nodeId; }
    public String getNodeLabel() { return nodeLabel; }
    public String getLocation() { return location; }
    public EventType getType() { return type; }
    public double getMagnitude() { return magnitude; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getPoissonLambda() { return poissonLambda; }
    public int getPoissonK() { return poissonK; }
    public double getPoissonProbability() { return poissonProbability; }
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    public double getWindSpeed() { return windSpeed; }
    public double getVibration() { return vibration; }
    public String getFormattedTime() { return timestamp.format(FORMATTER); }
    // Harita bazlı olay getter'ları
    public double getEventX() { return eventX; }
    public double getEventY() { return eventY; }
    public double getDistanceToSensor() { return distanceToSensor; }
    public double getDetectionRadius() { return detectionRadius; }
    public boolean isDetected() { return detected; }
}
