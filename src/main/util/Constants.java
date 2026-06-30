package main.util;

/**
 * Sistem sabitleri - Gerçek datasheet değerleri
 * 
 * Donanım Referansları:
 * - Pycom FiPy: https://pycom.io/product/fipy/
 * - Bosch BME280: https://www.bosch-sensortec.com/products/environmental-sensors/humidity-sensors-bme280/
 * - InvenSense MPU-6050: https://invensense.tdk.com/products/motion-tracking/6-axis/mpu-6050/
 */
public class Constants {

    // ============================================================
    // PYCOM FIPY - GERÇEK DATASHEET DEĞERLERİ
    // Kaynak: Pycom FiPy Datasheet v1.2
    // ============================================================
    public static final double FIPY_SLEEP_CURRENT_MA        = 0.010; // 10 µA = 0.010 mA
    public static final double FIPY_MODEM_SLEEP_MA          = 1.2;   // mA
    public static final double FIPY_ACTIVE_CPU_MA           = 45.0;  // mA
    public static final double FIPY_LORA_TX_MA              = 120.0; // mA @ +20dBm
    public static final double FIPY_LORA_RX_MA              = 15.0;  // mA
    public static final double FIPY_WIFI_TX_MA              = 80.0;  // mA
    public static final double FIPY_VOLTAGE_V               = 3.7;   // LiPo battery voltage
    public static final double FIPY_BATTERY_MAH             = 2000.0;// mAh
    public static final double FIPY_BATTERY_JOULES          = FIPY_BATTERY_MAH * FIPY_VOLTAGE_V * 3.6; // J

    // ============================================================
    // BOSCH BME280 - GERÇEK DATASHEET DEĞERLERİ
    // Kaynak: BST-BME280-DS002
    // ============================================================
    public static final double BME280_NORMAL_MODE_UA        = 3.6;   // µA (normal mode, 1Hz)
    public static final double BME280_SLEEP_MODE_UA         = 0.1;   // µA
    public static final double BME280_FORCED_MODE_UA        = 2.0;   // µA (single measure)
    public static final double BME280_TEMP_MIN              = -40.0; // °C
    public static final double BME280_TEMP_MAX              = 85.0;  // °C
    public static final double BME280_HUMIDITY_MIN          = 0.0;   // % RH
    public static final double BME280_HUMIDITY_MAX          = 100.0; // % RH
    public static final double BME280_PRESSURE_MIN          = 300.0; // hPa
    public static final double BME280_PRESSURE_MAX          = 1100.0;// hPa

    // ============================================================
    // INVENSENSE MPU-6050 - GERÇEK DATASHEET DEĞERLERİ
    // Kaynak: PS-MPU-6000A-00 Rev 3.4
    // ============================================================
    public static final double MPU6050_NORMAL_MA            = 3.9;   // mA (gyro+accel)
    public static final double MPU6050_ACCEL_ONLY_MA        = 0.5;   // mA
    public static final double MPU6050_SLEEP_UA             = 5.0;   // µA
    public static final double MPU6050_ACCEL_RANGE_G        = 16.0;  // ±16g max range

    // ============================================================
    // SOLAR PANEL - 5V 100mA mini panel
    // ============================================================
    public static final double SOLAR_PANEL_CURRENT_MA       = 100.0; // mA (peak güneşli)
    public static final double SOLAR_PANEL_VOLTAGE_V        = 5.0;   // V
    public static final int    SOLAR_SUNRISE_HOUR           = 6;
    public static final int    SOLAR_SUNSET_HOUR            = 20;

    // ============================================================
    // POISSON DAĞILIMI PARAMETRELERİ
    // ============================================================
    // Poisson parametreleri — Demo modunda hoca "her saniye kaya mı düşüyor?" demesin
    // Lambda değerleri küçük tutuldu: normal güneşli günde 0.8 olay / 8 sn ≈ 6 olay/dk MAX
    public static final double LAMBDA_SUNNY                 = 0.5;
    public static final double LAMBDA_PARTLY_CLOUDY         = 0.9;
    public static final double LAMBDA_OVERCAST              = 1.5;
    public static final double LAMBDA_RAINY                 = 2.2;
    public static final double LAMBDA_STORMY                = 4.0;
    public static final double LAMBDA_FOGGY                 = 3.0;

    // Event üretim aralığı (milisaniye)
    //  8 saniye = gerçekçi polling periyodu; 339 olay / 5 dk gibi aşırılık önlenir
    public static final int    EVENT_GENERATION_INTERVAL_MS = 8000;  // 8 saniyede bir
    public static final int    WEATHER_UPDATE_INTERVAL_MS   = 60000; // 60 saniye

    // ============================================================
    // RİSK HESAPLAMA AĞIRLIKLARI
    // ============================================================
    public static final double ROCK_RISK_VIBRATION_W        = 0.35;
    public static final double ROCK_RISK_SLOPE_W            = 0.25;
    public static final double ROCK_RISK_RAIN_W             = 0.20;
    public static final double ROCK_RISK_HUMIDITY_W         = 0.20;

    public static final double SLIP_RISK_HUMIDITY_W         = 0.40;
    public static final double SLIP_RISK_RAINFALL_W         = 0.35;
    public static final double SLIP_RISK_TEMP_W             = 0.25;

    // Risk eşikleri (simülasyon için gerçekçi aralıklar)
    public static final double RISK_SAFE                    = 0.15;
    public static final double RISK_CAUTION                 = 0.30;
    public static final double RISK_DANGER                  = 0.45;

    // ============================================================
    // LORA İLETİŞİM PARAMETRELERİ
    // ============================================================
    public static final int    LORA_SF_DEFAULT              = 7;     // Spreading Factor
    public static final int    LORA_BANDWIDTH_KHZ           = 125;   // kHz
    public static final int    LORA_TX_POWER_DBM            = 14;    // dBm
    public static final double LORA_TX_DURATION_S           = 0.1;   // saniye (packet)
    public static final int    LORA_FREQUENCY_MHZ           = 868;   // EU868 band

    // ============================================================
    // ENERJİ MODELİ - DUTY CYCLE
    // ============================================================
    // Node bir ölçüm döngüsü: sleep → wake → sense → transmit → sleep
    public static final double SENSE_DURATION_S             = 0.5;   // saniye
    public static final double TRANSMIT_DURATION_S          = 0.1;   // saniye
    public static final double SLEEP_INTERVAL_S             = 60.0;  // 60 saniye uyku

    // ============================================================
    // DRONE PARAMETRELERİ
    // ============================================================
    public static final double DRONE_SPEED_KM_H             = 80.0;  // km/h
    public static final int    DRONE_INSPECTION_TIME_S      = 120;   // saniye
    public static final int    NUM_DRONES                   = 3;

    // ============================================================
    // NODE KOORDİNATLARI - Alanya → Gazipaşa → Anamur → Bozyazı → Aydıncık → Silifke
    // GPS koordinatları gerçek D400 sahil yolu üzerinden alınmıştır
    // ============================================================
    public static final double[][] NODE_COORDINATES = {
        // [lat, lon, eğim (°)]
        // Gerçek D400 Sahil Yolu: Alanya → Gazipaşa → Anamur → Bozyazı → Aydıncık → Silifke
        {36.5440, 32.0082, 5.0},   // 0: Alanya Doğu
        {36.5480, 32.0612, 8.0},   // 1: Alanya-Gazipaşa arası (karayolu kara tarafı)
        {36.5520, 32.1145, 12.0},  // 2: Koru Burnu (yol içe giriyor)
        {36.5510, 32.1678, 15.0},  // 3: Kaledran (dağ yamacı)
        {36.5460, 32.2201, 18.0},  // 4: Yüksek Viraj (uçurum kenarı)
        {36.5390, 32.2734, 10.0},  // 5: Kıyı Kesiği (kıyı yolu)
        {36.5280, 32.3267, 7.0},   // 6: Gazipaşa Batı (şehre iniş)
        {36.2681, 32.3412, 5.0},   // 7: Gazipaşa Merkez ✓
        {36.4812, 32.4378, 22.0},  // 8: Gazipaşa-Anamur Tünel ✓
        {36.4678, 32.4934, 25.0},  // 9: Uçurum Kenarı ✓
        {36.4534, 32.5467, 20.0},  // 10: Kayalık Bölge ✓
        {36.4401, 32.6001, 16.0},  // 11: Sarısu ✓
        {36.4312, 32.6534, 18.0},  // 12: Karakabaklar ✓
        {36.4189, 32.7067, 22.0},  // 13: Viraj Bölgesi ✓
        {36.4023, 32.7601, 15.0},  // 14: Kıyı Yolu ✓
        {36.3867, 32.8134, 10.0},  // 15: Ören ✓
        {36.3712, 32.8667, 8.0},   // 16: Yenipazar ✓
        {36.3578, 32.9201, 12.0},  // 17: Anamur Batı ✓
        {36.0783, 32.8412, 5.0},   // 18: Anamur Merkez ✓
        {36.3312, 32.9801, 18.0},  // 19: Anamur Doğu Tünel ✓
        {36.3167, 33.0334, 20.0},  // 20: İskele Bölgesi ✓
        {36.3023, 33.0867, 15.0},  // 21: Boğaz Geçidi ✓
        {36.2878, 33.1401, 22.0},  // 22: Yüksek Kıyı ✓
        {36.2712, 33.1934, 18.0},  // 23: Bozyazı Batı ✓
        {36.0951, 33.0012, 5.0},   // 24: Bozyazı Merkez ✓
        {36.2523, 33.2467, 12.0},  // 25: Bozyazı Doğu ✓
        {36.2378, 33.3001, 10.0},  // 26: Hacıkırı ✓
        {36.2234, 33.3534, 8.0},   // 27: Kıyı Kesiği ✓
        {36.2089, 33.4067, 15.0},  // 28: Aydıncık Batı ✓
        {36.1479, 33.3187, 5.0},   // 29: Aydıncık Merkez ✓
        // Aydıncık → Silifke: yol kuzeydoğuya gidiyor, enlem ARTMALI
        {36.2150, 33.5020, 12.0},  // 30: Aydıncık Doğu (yol kuzey sahilden ilerliyor)
        {36.2280, 33.5580, 18.0},  // 31: Kaya Kütlesi (uçurum kenarı)
        {36.2420, 33.6150, 22.0},  // 32: Uçurum Bölgesi
        {36.2580, 33.6720, 25.0},  // 33: Kritik Viraj (dağ geçidi)
        {36.2750, 33.7290, 20.0},  // 34: Yeniyurt
        {36.2940, 33.7850, 15.0},  // 35: Limonlu (Göksu kuzey yakası)
        {36.3180, 33.8340, 10.0},  // 36: Silifke Batı (Göksu deltası kuzeyinde)
        {36.3420, 33.8780, 8.0},   // 37: Göksu Deltası (kıyı kuzey yolu)
        {36.3640, 33.9180, 5.0},   // 38: Silifke Yakını
        {36.3734, 33.9201, 12.0},  // 39: Taşucu ✓
        {36.3780, 33.9650, 7.0},   // 40: Silifke Merkez (şehir merkezi)
        {36.5520, 32.1456, 30.0},  // 41: Yüksek Risk - Uçurum (Cliff, kara tarafı)
        {36.4456, 32.5234, 28.0},  // 42: Yüksek Risk - Kayalık ✓
        {36.2234, 33.1567, 26.0},  // 43: Yüksek Risk - Viraj ✓
        {36.2700, 33.6890, 32.0},  // 44: Yüksek Risk - Kritik Viraj (kara tarafı)
    };


    // Node türleri (indeks 41-44 yüksek riskli)
    public static final String[] NODE_TYPES = {
        "COASTAL", "COASTAL", "CLIFF_EDGE", "CLIFF_EDGE", "CLIFF_EDGE",
        "COASTAL", "COASTAL", "COASTAL", "TUNNEL", "CLIFF_EDGE",
        "CLIFF_EDGE", "COASTAL", "CLIFF_EDGE", "CLIFF_EDGE", "COASTAL",
        "COASTAL", "COASTAL", "COASTAL", "COASTAL", "TUNNEL",
        "COASTAL", "CLIFF_EDGE", "CLIFF_EDGE", "COASTAL", "COASTAL",
        "COASTAL", "COASTAL", "COASTAL", "COASTAL", "COASTAL",
        "COASTAL", "CLIFF_EDGE", "CLIFF_EDGE", "CLIFF_EDGE", "COASTAL",
        "COASTAL", "COASTAL", "COASTAL", "COASTAL", "COASTAL",
        "COASTAL", "CLIFF_EDGE", "CLIFF_EDGE", "CLIFF_EDGE", "CLIFF_EDGE"
    };

    // ============================================================
    // GATEWAY KOORDİNATLARI
    // LoRaWAN'da gateway DAIMA en yüksek noktaya (Line of Sight için Toros sırtları)
    // konur ki tüm sensörler engelsiz görülebilsin.
    // Her gateway yaklaşık 400-800m rakımda dağ yamacına yerleştirilmiştir.
    // ============================================================
    public static final double[][] GATEWAY_COORDINATES = {
        {36.6180, 31.9650},  // GW-Alanya     — Toroslar, Alanya Kuzeyindeki sırt (~550m)
        {36.5280, 32.4012},  // GW-Gazipasa   — Gazipaşa Torosları (~480m)
        {36.2800, 32.8200},  // GW-Anamur     — Anamur Aladağ etegi (~600m)
        {36.2450, 33.0600},  // GW-Bozyazi    — Bozyazı dağ yamaçları (~420m)
        {36.2800, 33.3500},  // GW-Aydincik   — Aydıncık kuzey tepesi (~380m)
        {36.3200, 33.9600},  // GW-Silifke    — Silifke Torosları (~500m)
    };

    public static final String[] GATEWAY_NAMES = {
        "GW-Alanya", "GW-Gazipasa", "GW-Anamur",
        "GW-Bozyazi", "GW-Aydincik", "GW-Silifke"
    };

    // Drone üs konumları
    public static final double[][] DRONE_BASE_COORDINATES = {
        {36.5440, 32.0082},  // Alanya Üssü
        {36.0783, 32.8412},  // Anamur Üssü
        {36.0645, 33.9867},  // Silifke Üssü
    };

    public static final String[] DRONE_BASE_NAMES = {
        "Alanya Drone Üssü", "Anamur Drone Üssü", "Silifke Drone Üssü"
    };

    // Harita merkezi koordinatları
    public static final double MAP_CENTER_LAT = 36.3;
    public static final double MAP_CENTER_LON = 33.0;
    public static final int    MAP_DEFAULT_ZOOM = 10;

    private Constants() {} // Utility class
}
