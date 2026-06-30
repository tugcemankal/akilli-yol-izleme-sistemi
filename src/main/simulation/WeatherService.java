package main.simulation;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;

/**
 * Gerçek Zamanlı Hava Durumu Servisi
 * 
 * Open-Meteo API kullanır (tamamen ücretsiz, API key gerektirmez)
 * API: https://open-meteo.com/
 * 
 * Mersin-Antalya sahil yolu için konum: Alanya-Anamur-Silifke hattı
 * Kullanılan koordinat: Anamur (36.08°N, 32.84°E) - bölge merkezi
 */
public class WeatherService {

    // Open-Meteo API endpoint
    private static final String API_URL = 
        "https://api.open-meteo.com/v1/forecast?" +
        "latitude=36.08&longitude=32.84" +
        "&current=temperature_2m,relative_humidity_2m,precipitation," +
        "wind_speed_10m,weather_code,cloud_cover,visibility,surface_pressure" +
        "&wind_speed_unit=kmh&timezone=Europe/Istanbul";

    // Mevcut hava verileri
    private double temperature = 22.0;      // °C
    private double humidity = 65.0;         // % RH
    private double precipitation = 0.0;     // mm/h
    private double windSpeed = 15.0;        // km/h
    private double pressure = 1015.0;       // hPa
    private double visibility = 20000.0;    // metre
    private int cloudCover = 20;            // %
    private int weatherCode = 0;            // WMO kod
    private String weatherCondition = "SUNNY";
    private String weatherDescription = "Açık Hava";
    private String weatherEmoji = "☀️";

    // Durum
    private boolean apiAvailable = false;
    private long lastUpdateTime = 0;
    private String lastError = "";

    /**
     * Hava durumunu API'dan güncelle
     * @return true eğer başarılı
     */
    public boolean fetchWeather() {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "MersinAntalyaWSN/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                lastError = "HTTP " + responseCode;
                return false;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            JSONObject current = json.getJSONObject("current");

            this.temperature    = current.getDouble("temperature_2m");
            this.humidity       = current.getDouble("relative_humidity_2m");
            this.precipitation  = current.getDouble("precipitation");
            this.windSpeed      = current.getDouble("wind_speed_10m");
            this.weatherCode    = current.getInt("weather_code");
            this.pressure       = current.optDouble("surface_pressure", 1013.0);
            this.visibility     = current.optDouble("visibility", 20000.0);
            this.cloudCover     = current.optInt("cloud_cover", 0);

            // Hava kodu → kondisyon dönüşümü (WMO kodları)
            parseWeatherCode(weatherCode);

            this.apiAvailable = true;
            this.lastUpdateTime = System.currentTimeMillis();
            this.lastError = "";
            return true;

        } catch (Exception e) {
            lastError = e.getMessage();
            apiAvailable = false;
            // API yoksa simüle et
            simulateWeather();
            return false;
        }
    }

    /**
     * WMO hava kodu çözümleme
     * https://open-meteo.com/en/docs#weathervariables
     */
    private void parseWeatherCode(int code) {
        if (code == 0) {
            weatherCondition = "SUNNY";
            weatherDescription = "Açık Hava";
            weatherEmoji = "☀️";
        } else if (code <= 3) {
            weatherCondition = "PARTLY_CLOUDY";
            weatherDescription = "Parçalı Bulutlu";
            weatherEmoji = "⛅";
        } else if (code <= 49) {
            weatherCondition = "FOGGY";
            weatherDescription = "Sisli";
            weatherEmoji = "🌫️";
        } else if (code <= 67) {
            weatherCondition = "RAINY";
            weatherDescription = "Yağmurlu";
            weatherEmoji = "🌧️";
        } else if (code <= 77) {
            weatherCondition = "RAINY";
            weatherDescription = "Kar Yağışlı";
            weatherEmoji = "❄️";
        } else if (code <= 82) {
            weatherCondition = "RAINY";
            weatherDescription = "Sağanak";
            weatherEmoji = "⛈️";
        } else {
            weatherCondition = "STORMY";
            weatherDescription = "Fırtınalı";
            weatherEmoji = "🌩️";
        }

        // Nem ve bulut örtüsüne göre ek düzeltme
        if (humidity > 90 && cloudCover > 80) {
            weatherCondition = "FOGGY";
            weatherDescription = "Sis / Yoğun Nem";
            weatherEmoji = "🌫️";
        }
    }

    /**
     * API yokken gerçekçi simülasyon
     * Günün saatine ve rastgeleliğe göre değişir
     */
    private void simulateWeather() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();

        // Sıcaklık: gündüz sıcak, gece serin
        double baseTem = 18.0 + 10.0 * Math.sin(Math.PI * (hour - 6) / 18.0);
        this.temperature = baseTem + (Math.random() - 0.5) * 3.0;

        // Nem: sabah erken ve gece yüksek
        double baseHum = 65.0 - 20.0 * Math.sin(Math.PI * (hour - 6) / 18.0);
        this.humidity = Math.max(40, Math.min(95, baseHum + (Math.random() - 0.5) * 10));

        // Rüzgar: öğleden sonra güçlü
        this.windSpeed = 10.0 + 15.0 * Math.sin(Math.PI * hour / 24.0) + Math.random() * 10;

        // Basınç
        this.pressure = 1013.0 + (Math.random() - 0.5) * 15.0;

        // Yağış: %30 olasılıkla
        this.precipitation = Math.random() < 0.3 ? Math.random() * 5.0 : 0.0;

        // Bulut
        this.cloudCover = (int)(Math.random() * 100);

        parseWeatherCode(cloudCover > 80 ? 3 : (precipitation > 0 ? 61 : 0));
    }

    /**
     * Gece mi yoksa gündüz mü?
     */
    public boolean isDaytime() {
        int hour = LocalTime.now().getHour();
        return hour >= 6 && hour < 20;
    }

    /**
     * Solar şarj çarpanı (0.0 - 1.0)
     */
    public double getSolarChargeFactor() {
        if (!isDaytime()) return 0.0;
        int hour = LocalTime.now().getHour();
        // Öğlen 1.0, sabah/akşam 0.3
        double solarFactor = Math.sin(Math.PI * (hour - 6) / 14.0);
        // Bulut örtüsü azaltır
        return solarFactor * (1.0 - cloudCover / 150.0);
    }

    // Getters
    public double getTemperature() { return temperature; }
    public double getHumidity() { return humidity; }
    public double getPrecipitation() { return precipitation; }
    public double getWindSpeed() { return windSpeed; }
    public double getPressure() { return pressure; }
    public double getVisibility() { return visibility; }
    public int getCloudCover() { return cloudCover; }
    public int getWeatherCode() { return weatherCode; }
    public String getWeatherCondition() { return weatherCondition; }
    public String getWeatherDescription() { return weatherDescription; }
    public String getWeatherEmoji() { return weatherEmoji; }
    public boolean isApiAvailable() { return apiAvailable; }
    public String getLastError() { return lastError; }
    public long getLastUpdateTime() { return lastUpdateTime; }
}
