package main.simulation;

import main.model.RiskEvent;
import main.model.SensorNode;
import main.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Poisson Olay Üreteci
 *
 * Matematiksel Model:
 * P(X=k) = (e^(-λ) * λ^k) / k!
 *
 * Bu sınıf, gerçek zamanlı Poisson sürecini simüle eder.
 * İki modda çalışır:
 *   1. Düğüm bazlı (eski mod): Her node için üretim.
 *   2. Harita bazlı (hoca yönergesi): Tüm harita alanında rastgele (X,Y)
 *      koordinatında olay üretilir; algılama kontrolü Öklid mesafesiyle yapılır.
 *
 * Poisson sürecinde:
 * - λ = birim zamandaki ortalama olay sayısı
 * - Olaylar bağımsız ve rastgeledir
 * - Zaman aralıkları üstel dağılıma uyar
 */
public class PoissonEngine {

    private final Random random;
    private double lambda;           // Mevcut λ değeri
    private String weatherCondition;

    // Poisson istatistikleri
    private long totalEventsGenerated;
    private final List<Integer> eventCountHistory;  // k değerleri
    private final List<Double> lambdaHistory;        // λ değerleri

    public PoissonEngine(double initialLambda) {
        this.random = new Random();
        this.lambda = initialLambda;
        this.weatherCondition = "SUNNY";
        this.totalEventsGenerated = 0;
        this.eventCountHistory = new ArrayList<>();
        this.lambdaHistory = new ArrayList<>();
    }

    /**
     * Poisson PMF: P(X = k) = e^(-λ) * λ^k / k!
     * 
     * @param k olay sayısı
     * @param lam λ parametresi
     * @return olasılık
     */
    public static double poissonPMF(int k, double lam) {
        if (k < 0) return 0.0;
        double logP = -lam + k * Math.log(lam + 1e-10) - logFactorial(k);
        return Math.exp(logP);
    }

    /**
     * Log faktöriyel hesabı (büyük sayılar için Stirling yaklaşımı)
     */
    private static double logFactorial(int n) {
        if (n <= 1) return 0.0;
        double result = 0.0;
        for (int i = 2; i <= n; i++) {
            result += Math.log(i);
        }
        return result;
    }

    /**
     * Poisson dağılımından k değeri üret
     * Knuth algoritması kullanılır:
     * L = e^(-λ), k=0, p=1
     * Tekrar: k++, p *= U(0,1)
     * k-1 döndür (p < L olana kadar)
     */
    public int generatePoissonSample() {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;

        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);

        return k - 1;
    }

    /**
     * Bir node için olay üret
     * 
     * @param node hedef node
     * @param baseTemperature hava sıcaklığı
     * @param baseHumidity hava nemi
     * @param baseWindSpeed rüzgar hızı
     * @return üretilen olaylar listesi (boş olabilir)
     */
    public List<RiskEvent> generateEventsForNode(SensorNode node,
                                                   double baseTemperature,
                                                   double baseHumidity,
                                                   double baseWindSpeed) {
        List<RiskEvent> events = new ArrayList<>();
        if (!node.isActive()) return events;

        // Bu node için λ değerini hesapla (node tipine göre çarpan)
        double nodeLambda = lambda;
        if (node.getType().equals("CLIFF_EDGE")) {
            nodeLambda *= 1.4; // Uçurum kenarları daha riskli
        } else if (node.getType().equals("TUNNEL")) {
            nodeLambda *= 1.2; // Tüneller sis için daha riskli
        }

        // Poisson örneklemesi: kaç olay olacak?
        int k = generatePoissonSample();

        // Olasılığı hesapla
        double probability = poissonPMF(k, nodeLambda);

        // İstatistik güncelle
        lambdaHistory.add(nodeLambda);
        eventCountHistory.add(k);
        if (lambdaHistory.size() > 1000) {
            lambdaHistory.remove(0);
            eventCountHistory.remove(0);
        }

        // k olay üret
        for (int i = 0; i < k; i++) {
            RiskEvent.EventType eventType = selectEventType(baseHumidity, baseTemperature,
                                                             baseWindSpeed, node.getType());

            double magnitude = 0.1 + random.nextDouble() * 0.9;
            // Büyük λ ile daha şiddetli olaylar
            magnitude = Math.min(1.0, magnitude * (nodeLambda / Constants.LAMBDA_SUNNY));

            RiskEvent event = new RiskEvent(
                node.getId(),
                node.getNodeId(),
                node.getLocation(),
                eventType,
                magnitude,
                nodeLambda,
                k,
                probability,
                baseTemperature + (random.nextDouble() - 0.5) * 3,
                baseHumidity,
                baseWindSpeed,
                node.getVibration()
            );

            events.add(event);
            node.addEvent(event);
            totalEventsGenerated++;
        }

        return events;
    }
    /**
     * =================================================================
     * HARITA BAZLI OLAY ÜRETİMİ  (Hoca Yönergesi)
     * =================================================================
     *
     * Hoca yönergesi: "Poisson algoritması, belli bir düğüme olay atamak
     * yerine, tüm harita alanında rastgele bir X, Y koordinatında olay
     * (piksel) üretmeli."
     *
     * @param mapWidth        Harita genişliği (normalize)
     * @param mapHeight       Harita yüksekliği (normalize)
     * @param effectiveLambda Toplam harita lambdası.
     *                        Matematiksel gerekçe: Eski sistemde her N aktif
     *                        node için ayrı Poisson(λ) üretilirdi → toplam
     *                        beklenen olay = N×λ. Harita bazlı sistemde aynı
     *                        toplam oranı korumak için effectiveLambda = N×λ
     *                        kullanılır. Böylece istatistiksel tutarlılık sağlanır.
     */
    public List<MapEvent> generateMapBasedEvents(double mapWidth, double mapHeight,
                                                  double effectiveLambda) {
        List<MapEvent> events = new ArrayList<>();

        // Knuth algoritması: effectiveLambda ile k üret
        // (lambda yerine effectiveLambda kullanılıyor)
        int k = generatePoissonSampleFor(effectiveLambda);

        // İstatistik güncelle (asıl lambda değeri kaydedilir)
        eventCountHistory.add(k);
        lambdaHistory.add(lambda);
        if (lambdaHistory.size() > 1000) {
            lambdaHistory.remove(0);
            eventCountHistory.remove(0);
        }

        // k adet olay için rastgele (X, Y) koordinatı üret
        for (int i = 0; i < k; i++) {
            double x = random.nextDouble() * mapWidth;   // X ekseni — Poisson dağılımı
            double y = random.nextDouble() * mapHeight;  // Y ekseni — Poisson dağılımı
            events.add(new MapEvent(x, y, lambda, k, poissonPMF(k, lambda)));
            totalEventsGenerated++;
        }

        return events;
    }

    /** Geriye dönük uyumluluk — varsayılan olarak this.lambda kullanır */
    public List<MapEvent> generateMapBasedEvents(double mapWidth, double mapHeight) {
        return generateMapBasedEvents(mapWidth, mapHeight, lambda);
    }

    /**
     * Belirli bir lambda değeri için Knuth Poisson örneklemesi.
     * generatePoissonSample() this.lambda kullanır; bu metod herhangi bir lam için çalışır.
     */
    public int generatePoissonSampleFor(double lam) {
        if (lam <= 0) return 0;
        double L = Math.exp(-lam);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > L);
        return k - 1;
    }


    /**

     * Harita bazlı bir olayın belirli bir sensör tarafından algılanıp
     * algılanmadığını kontrol eder.
     *
     * Hoca formülü: d = √((x_olay - x_sensor)² + (y_olay - y_sensor)²)
     * Algılama koşulu: d ≤ r
     *
     * @param event       Harita olayı
     * @param sensorX     Sensör X koordinatı (normalize harita birimi)
     * @param sensorY     Sensör Y koordinatı (normalize harita birimi)
     * @param sensorRadius Sensörün algılama yarıçapı r
     * @return true → olay algılandı (d ≤ r)
     */
    public static boolean isEventDetectedBy(MapEvent event, double sensorX,
                                             double sensorY, double sensorRadius) {
        // Öklid mesafe formülü
        double dx = event.x - sensorX;
        double dy = event.y - sensorY;
        double distance = Math.sqrt(dx * dx + dy * dy); // d = √(Δx² + Δy²)
        event.lastDistance = distance;                  // log için sakla
        return distance <= sensorRadius;                // d ≤ r → algılandı
    }

    // ============================================================
    // MapEvent: Harita üzerindeki tek bir olay konumu
    // ============================================================
    public static class MapEvent {
        /** Olayın haritadaki X koordinatı */
        public final double x;
        /** Olayın haritadaki Y koordinatı */
        public final double y;
        /** O andaki lambda değeri */
        public final double lambda;
        /** O adımdaki toplam k (olay sayısı) */
        public final int k;
        /** P(X=k) olasılığı */
        public final double probability;
        /** Öklid mesafesi — isEventDetectedBy() tarafından doldurulur */
        public double lastDistance = 0;

        public MapEvent(double x, double y, double lambda, int k, double probability) {
            this.x = x;
            this.y = y;
            this.lambda = lambda;
            this.k = k;
            this.probability = probability;
        }
    }


    private RiskEvent.EventType selectEventType(double humidity, double temperature,
                                                  double windSpeed, String nodeType) {
        double r = random.nextDouble();

        // Sis riski: yüksek nem + sıcak-soğuk fark
        if (humidity > 85 && r < 0.35) {
            return RiskEvent.EventType.FOG_FORMATION;
        }

        // Kaygan yol: yüksek nem + soğuk hava
        if (humidity > 75 && temperature < 10 && r < 0.30) {
            return RiskEvent.EventType.ROAD_SLIPPERY;
        }

        // Kaya titreşimi: uçurum kenarlarında daha sık
        if (nodeType.equals("CLIFF_EDGE") && r < 0.40) {
            return RiskEvent.EventType.ROCK_VIBRATION;
        }

        // Mikro heyelan: yüksek nem + eğimli arazi
        if (humidity > 80 && nodeType.equals("CLIFF_EDGE") && r < 0.20) {
            return RiskEvent.EventType.MICRO_LANDSLIDE;
        }

        // Şiddetli rüzgar
        if (windSpeed > 50 && r < 0.25) {
            return RiskEvent.EventType.STRONG_WIND;
        }

        // Ani yağış
        if (weatherCondition.contains("RAIN") || weatherCondition.contains("STORM")) {
            if (r < 0.30) return RiskEvent.EventType.RAIN_SURGE;
        }

        // Düşük görüş (tünel veya sis)
        if (nodeType.equals("TUNNEL") && r < 0.20) {
            return RiskEvent.EventType.VISIBILITY_LOW;
        }

        // Varsayılan: ağırlıklı rastgele
        RiskEvent.EventType[] types = RiskEvent.EventType.values();
        return types[(int)(r * types.length) % types.length];
    }

    /**
     * Hava koşuluna göre λ değerini güncelle
     */
    public void updateLambdaForWeather(String weather) {
        this.weatherCondition = weather;
        switch (weather) {
            case "SUNNY":       this.lambda = Constants.LAMBDA_SUNNY;         break;
            case "PARTLY_CLOUDY": this.lambda = Constants.LAMBDA_PARTLY_CLOUDY; break;
            case "OVERCAST":    this.lambda = Constants.LAMBDA_OVERCAST;      break;
            case "RAINY":       this.lambda = Constants.LAMBDA_RAINY;         break;
            case "STORMY":      this.lambda = Constants.LAMBDA_STORMY;        break;
            case "FOGGY":       this.lambda = Constants.LAMBDA_FOGGY;         break;
            default:            this.lambda = Constants.LAMBDA_SUNNY;
        }
    }

    /**
     * Poisson dağılımını görselleştirme için hesapla
     * @param maxK maksimum k değeri
     * @return her k için P(X=k) dizisi
     */
    public double[] getPoissonDistribution(int maxK) {
        double[] dist = new double[maxK + 1];
        for (int k = 0; k <= maxK; k++) {
            dist[k] = poissonPMF(k, lambda);
        }
        return dist;
    }

    /**
     * Beklenen değer E[X] = λ
     */
    public double getExpectedValue() { return lambda; }

    /**
     * Varyans Var[X] = λ
     */
    public double getVariance() { return lambda; }

    /**
     * Standart sapma
     */
    public double getStdDev() { return Math.sqrt(lambda); }

    // Getters ve Setters
    public double getLambda() { return lambda; }
    public void setLambda(double lambda) { this.lambda = Math.max(0.1, Math.min(15.0, lambda)); }
    public long getTotalEventsGenerated() { return totalEventsGenerated; }
    public List<Integer> getEventCountHistory() { return eventCountHistory; }
    public List<Double> getLambdaHistory() { return lambdaHistory; }
    public String getWeatherCondition() { return weatherCondition; }
}
