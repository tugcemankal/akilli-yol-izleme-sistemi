package main.simulation;

import main.model.SensorNode;
import main.util.Constants;

/**
 * Risk Hesaplama Motoru
 * 
 * Mersin-Antalya yolu için gerçekçi risk modeli:
 * 
 * 1. Kaya Düşmesi Riski:
 *    R_rock = 0.35 * vibration + 0.25 * slope + 0.20 * rain + 0.20 * humidity
 * 
 * 2. Sis Riski:
 *    R_fog = humidity * |T - T_dew| normalizasyonu
 * 
 * 3. Kaygan Yol Riski:
 *    R_slip = 0.40 * humidity + 0.35 * rainfall + 0.25 * coldTemp
 */
public class RiskCalculator {

    /**
     * Tüm risk değerlerini hesapla ve node'u güncelle
     */
    public static void calculateRisk(SensorNode node, double rainfall) {
        double rockRisk = calculateRockRisk(node, rainfall);
        double fogRisk  = calculateFogRisk(node);
        double slipRisk = calculateSlipRisk(node, rainfall);

        node.updateRiskLevels(rockRisk, fogRisk, slipRisk);
    }

    /**
     * KAYA DÜŞMESİ RİSKİ
     * 
     * Bileşenler:
     * - Titreşim ivmesi (MPU-6050): düşük taş hareketi göstergesi
     * - Eğim açısı: yüksek eğimde kaya düşme ihtimali artar
     * - Yağış miktarı: yağış zemin bağını gevşetir
     * - Nem: yüksek nem toprak bütünlüğünü bozar
     */
    public static double calculateRockRisk(SensorNode node, double rainfall) {
        // Normalize vibration (0-2g arası tipik değerler → 0-1)
        double normVibration = Math.min(1.0, node.getVibration() / 2.0);

        // Normalize eğim (0-45° arası → 0-1)
        double normSlope = Math.min(1.0, node.getSlopeAngle() / 45.0);

        // Normalize yağış (0-50mm/h arası → 0-1)
        double normRain = Math.min(1.0, rainfall / 50.0);

        // Normalize nem (40-100% arası → 0-1)
        double normHumidity = Math.max(0, (node.getHumidity() - 40) / 60.0);

        double risk = Constants.ROCK_RISK_VIBRATION_W * normVibration
                    + Constants.ROCK_RISK_SLOPE_W     * normSlope
                    + Constants.ROCK_RISK_RAIN_W      * normRain
                    + Constants.ROCK_RISK_HUMIDITY_W  * normHumidity;

        // Cliff Edge için çarpan
        if (node.getType().equals("CLIFF_EDGE")) {
            risk = Math.min(1.0, risk * 1.3);
        }

        return Math.max(0, Math.min(1, risk));
    }

    /**
     * SİS RİSKİ
     * 
     * Meteoroloji formülü:
     * T_dew ≈ T - (100 - RH) / 5  (Magnus yaklaşımı, basit versiyon)
     * Sis oluşur: |T - T_dew| < 3°C
     * 
     * Risk = f(nem, T-T_dew, rüzgar)
     */
    public static double calculateFogRisk(SensorNode node) {
        double T = node.getTemperature();
        double RH = node.getHumidity();
        double wind = node.getWindSpeed();

        // Çiy noktası hesabı (Magnus formülü basitleştirilmiş)
        double T_dew = T - (100.0 - RH) / 5.0;

        // Sıcaklık-çiy noktası farkı
        double deltaT = Math.abs(T - T_dew);

        // Sis riski: fark küçüldükçe risk artar
        // deltaT < 1°C → çok yüksek sis riski
        // deltaT > 10°C → düşük sis riski
        double fogIntensity = Math.max(0, 1.0 - deltaT / 10.0);

        // Nem çarpanı: RH > 85% → sis başlar
        double humidityFactor = Math.max(0, (RH - 70.0) / 30.0);

        // Yüksek rüzgar sisi dağıtır
        double windFactor = Math.max(0.1, 1.0 - wind / 40.0);

        // Tünel için sis daha tehlikeli
        double tunnelFactor = node.getType().equals("TUNNEL") ? 1.3 : 1.0;

        double risk = fogIntensity * humidityFactor * windFactor * tunnelFactor;
        return Math.max(0, Math.min(1, risk));
    }

    /**
     * KAYGAN YOL RİSKİ
     * 
     * R_slip = 0.40 * normHumidity + 0.35 * normRainfall + 0.25 * coldFactor
     * 
     * Soğuk sıcaklık (<5°C): buz/kırağı riski
     * Yüksek nem: ıslak yüzey
     * Yağış: doğrudan ıslaklık
     */
    public static double calculateSlipRisk(SensorNode node, double rainfall) {
        double T = node.getTemperature();
        double RH = node.getHumidity();

        // Normalize nem (50-100% arası)
        double normHumidity = Math.max(0, (RH - 50.0) / 50.0);

        // Normalize yağış (0-30mm/h arası)
        double normRainfall = Math.min(1.0, rainfall / 30.0);

        // Soğuk faktörü: T < 5°C → kırağı/buz riski
        // T < 0°C → maksimum risk
        double coldFactor = 0.0;
        if (T < 5.0) {
            coldFactor = Math.min(1.0, (5.0 - T) / 10.0);
        }

        double risk = Constants.SLIP_RISK_HUMIDITY_W  * normHumidity
                    + Constants.SLIP_RISK_RAINFALL_W  * normRainfall
                    + Constants.SLIP_RISK_TEMP_W       * coldFactor;

        return Math.max(0, Math.min(1, risk));
    }

    /**
     * Görüş mesafesini hava koşullarına göre hesapla (metre)
     * Koschmieder kanunu basitleştirilmiş versiyonu
     */
    public static double calculateVisibility(double humidity, double windSpeed, double rainfall) {
        // Temel görüş mesafesi (açık havada): 20 km
        double baseVisibility = 20000.0;

        // Nem azaltma (yüzde)
        if (humidity > 90) {
            baseVisibility *= 0.1;  // Yoğun sis: 2 km
        } else if (humidity > 80) {
            baseVisibility *= 0.3;  // Sis: 6 km
        } else if (humidity > 70) {
            baseVisibility *= 0.6;  // Puslu: 12 km
        }

        // Yağış azaltma
        baseVisibility *= Math.max(0.05, 1.0 - rainfall / 50.0);

        // Rüzgar sisi dağıtır
        baseVisibility *= Math.min(1.5, 1.0 + windSpeed / 100.0);

        return Math.max(100, baseVisibility);
    }

    /**
     * Genel tehlike değerlendirmesi
     */
    public static String getDangerAssessment(SensorNode node) {
        double risk = node.getOverallRisk();
        String type = node.getType();

        if (risk >= Constants.RISK_DANGER) {
            if (type.equals("CLIFF_EDGE")) {
                return "⛔ KRİTİK: Kaya düşmesi tehlikesi. YOL KAPATILACAK.";
            } else if (type.equals("TUNNEL")) {
                return "⛔ KRİTİK: Sıfır görüş. Tünel kapatılacak.";
            } else {
                return "⛔ KRİTİK: Ağır hava koşulları. Yol kapanabilir.";
            }
        } else if (risk >= Constants.RISK_CAUTION) {
            return "⚠️ TEHLİKELİ: Dikkatli sürüş. Hız kısıtlaması uygulanıyor.";
        } else if (risk >= Constants.RISK_SAFE) {
            return "🟡 DİKKAT: Hafif risk. Koşullar izleniyor.";
        } else {
            return "✅ GÜVENLİ: Normal koşullar.";
        }
    }
}
