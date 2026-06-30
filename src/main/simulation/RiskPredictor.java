package main.simulation;

import main.model.SensorNode;
import java.util.List;

/**
 * Risk Tahmin Motoru (Prediction Engine)
 * 
 * Gelecek 10 dakika için istatistiksel risk tahmini yapar.
 * - Poisson λ değeri baz alınarak event olasılığı
 * - Hava durumu trend analizi
 * - Node kritiklik yüzdesi
 * 
 * P(X ≥ 1) = 1 - e^(-λt) formülü kullanılır.
 */
public class RiskPredictor {

    private final SimulationController sim;

    // Tahmin zaman ufku (dakika cinsinden)
    private static final int FORECAST_WINDOW_MIN = 10;

    // Son güncelleme cache
    private double cachedFogProb     = 0.0;
    private double cachedRockProb    = 0.0;
    private double cachedSlipProb    = 0.0;
    private double cachedStormProb   = 0.0;
    private String cachedAdvice      = "Simülasyonu başlatın.";
    private long   lastUpdateMs      = 0;

    public RiskPredictor(SimulationController sim) {
        this.sim = sim;
    }

    /** Tahminleri güncelle (en az 5 saniyede bir) */
    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateMs < 5000) return;
        lastUpdateMs = now;

        WeatherService w = sim.getWeatherService();
        PoissonEngine  p = sim.getPoissonEngine();
        List<SensorNode> nodes = sim.getNodes();

        double lambda = p.getLambda();
        double t      = FORECAST_WINDOW_MIN / 60.0; // saat cinsinden

        // P(X≥1) = 1 - e^(-λt): Herhangi bir olay olasılığı
        double pAtLeastOne = 1.0 - Math.exp(-lambda * t);

        // ── Sis Olasılığı ──────────────────────────────────────────
        // Nem >75% ve görüş <8km → sis birikebilir
        double humFactor  = Math.max(0, (w.getHumidity() - 70.0) / 30.0);   // 0-1
        double visFactor  = Math.max(0, (8000.0 - w.getVisibility()) / 8000.0); // 0-1
        cachedFogProb = Math.min(0.98, pAtLeastOne * 0.4 + humFactor * 0.35 + visFactor * 0.25);

        // ── Kaya/Heyelan Olasılığı ─────────────────────────────────
        // Yağış >2mm + kritik node yüzdesi
        double rainFactor = Math.min(1.0, w.getPrecipitation() / 5.0);
        double critFactor = criticalNodeRatio(nodes);
        cachedRockProb = Math.min(0.97, pAtLeastOne * 0.35 + rainFactor * 0.40 + critFactor * 0.25);

        // ── Yol Kayması Olasılığı ──────────────────────────────────
        // Nem yüksek + rüzgar yüksek + yağmur
        double windFactor = Math.min(1.0, w.getWindSpeed() / 60.0);
        cachedSlipProb = Math.min(0.95, pAtLeastOne * 0.30 + rainFactor * 0.35 + windFactor * 0.20 + humFactor * 0.15);

        // ── Fırtına/Şiddetli Hava Olasılığı ───────────────────────
        double stormBase = (w.getWindSpeed() > 40 ? 0.3 : 0.0)
                         + (w.getPrecipitation() > 3 ? 0.25 : 0.0)
                         + (w.getCloudCover() > 80 ? 0.15 : 0.0);
        cachedStormProb = Math.min(0.93, stormBase + pAtLeastOne * 0.30);

        // ── Tavsiye Metni ──────────────────────────────────────────
        cachedAdvice = buildAdvice();
    }

    private double criticalNodeRatio(List<SensorNode> nodes) {
        if (nodes.isEmpty()) return 0.0;
        long critical = nodes.stream()
            .filter(n -> n.isActive() && n.getRiskLevel() == SensorNode.RiskLevel.CRITICAL)
            .count();
        long active   = nodes.stream().filter(SensorNode::isActive).count();
        return active == 0 ? 0.0 : (double) critical / active;
    }

    private String buildAdvice() {
        double maxRisk = Math.max(Math.max(cachedFogProb, cachedRockProb),
                                  Math.max(cachedSlipProb, cachedStormProb));
        if (maxRisk > 0.80)
            return "⚠️ YÜKSEK RİSK: Anlık müdahale gerekli!";
        else if (maxRisk > 0.55)
            return "⚡ ORTA RİSK: Devriye artırılmalı.";
        else if (maxRisk > 0.30)
            return "ℹ️ DÜŞÜK RİSK: İzleme devam et.";
        else
            return "✅ RİSK YOK: Güvenli koşullar.";
    }

    // ── Getterlar ──────────────────────────────────────────────────

    public double getFogProbability()   { return cachedFogProb; }
    public double getRockProbability()  { return cachedRockProb; }
    public double getSlipProbability()  { return cachedSlipProb; }
    public double getStormProbability() { return cachedStormProb; }
    public String getAdvice()           { return cachedAdvice; }
    public int    getForecastWindowMin(){ return FORECAST_WINDOW_MIN; }
}
