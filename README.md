# 🛡️ Mersin-Antalya Akıllı Risk Erken Uyarı Sistemi
### Kablosuz Algılayıcı Ağ (WSN) — Digital Twin Simülasyonu

[![Java](https://img.shields.io/badge/Java-11-orange?style=flat-square&logo=java)](https://www.java.com/)
[![LoRaWAN](https://img.shields.io/badge/LoRaWAN-EU868-blue?style=flat-square)](https://lora-alliance.org/)
[![Poisson](https://img.shields.io/badge/Model-Poisson_Distribution-purple?style=flat-square)]()
[![License](https://img.shields.io/badge/License-Academic-green?style=flat-square)]()

> **D400 Sahil Yolu boyunca 45 sensör düğümü, 3 LoRaWAN Gateway ve otonom keşif drone'ları ile kaya düşmesi, sis ve kaygan yol risklerini gerçek zamanlı izleyen bir WSN simülasyonu (Ödev 1) ve bu verileri J48 & Naive Bayes algoritmalarıyla analiz edip tehlikeleri anında sınıflandıran Makine Öğrenmesi sistemi (Ödev 2).**

---

## 📋 İçindekiler

- [Proje Hakkında](#-proje-hakkında)
- [Ödev 1: WSN Simülasyonu ve Veri Üretimi](#-ödev-1-wsn-simülasyonu-ve-veri-üretimi)
- [Ödev 2: Makine Öğrenmesi ile Trafik Sınıflandırma](#-ödev-2-makine-öğrenmesi-ile-trafik-sınıflandırma)
- [Tam Sistem Akışı (İki Ödevin Bağlantısı)](#-tam-sistem-akışı-i̇ki-ödevin-bağlantısı)
- [Donanım Bileşenleri](#-donanım-bileşenleri)
- [Kurulum ve Çalıştırma](#-kurulum-ve-çalıştırma)
- [Proje Yapısı](#-proje-yapısı)
- [Özellikler](#-özellikler)
- [Ekran Görüntüleri](#-ekran-görüntüleri)

---

## 🎯 Proje Hakkında

Türkiye'nin en tehlikeli kıyı yollarından biri olan **Mersin-Antalya D400 sahil yolu**nda
gerçek zamanlı risk izleme ve erken uyarı yapan bir WSN sistemi simülasyonudur.

**Güzergah:** Alanya → Gazipaşa → Anamur → Bozyazı → Aydıncık → Silifke (~280 km)

| Metrik | Değer |
|--------|-------|
| 🔵 Sensör Düğümü | 45 node (28 COASTAL + 13 CLIFF_EDGE + 4 TUNNEL) |
| 🛰️ LoRaWAN Gateway | 3 adet (Alanya, Anamur, Silifke) |
| 🚁 Drone Üssü | 3 adet (otonom keşif filosu) |
| 📡 Frekans Bandı | EU868 MHz |
| ⚡ Protokol | LoRaWAN 1.0.3 |
| 🧠 ML Algoritmaları | J48 Karar Ağacı (%79.1), Naive Bayes (%74.9) |
| 📦 Veri Seti | 1596 kayıt, 4 özellik (Simülasyondan üretilen dataset.csv) |

---

## 📡 Ödev 1: WSN Simülasyonu ve Veri Üretimi

### Matematiksel Model ve Poisson Dağılımı

### Poisson Dağılımı

Sistem "harita bazlı" olay üretimi yapar — hoca yönergesine tam uygun:

```
P(X = k) = (e^{-λ} × λ^k) / k!
```

Lambda değeri hava koşullarına göre dinamik güncellenir:

| Hava | λ | Açıklama |
|------|---|----------|
| Güneşli | 0.50 | Düşük risk |
| Bulutlu | 1.50 | Orta risk |
| Yağmurlu | 2.20 | Yüksek risk |
| Fırtınalı | 4.00 | Kritik |
| Sisli | 3.00 | Görüş sıfır |

### Algılama Yarıçapı

```
r = √(A/π)          ← Hoca yönergesi: "Çap/alan oranı gibi matematiksel olarak modellenecek"
```

### Öklid Mesafe Kontrolü

```
d = √[(x_olay - x_sensör)² + (y_olay - y_sensör)²]
Algılandı  ⟺  d ≤ r
```

### Risk Formülleri

**Kaya Düşmesi:**
```
R_rock = 0.35×vibration_norm + 0.25×slope_norm + 0.20×rain_norm + 0.20×humidity_norm
```

**Sis Riski (Magnus formülü):**
```
T_çiy ≈ T - (100 - RH) / 5
R_fog = fogIntensity × humidityFactor × windFactor × tunnelFactor
```

**Kaygan Yol:**
```
R_slip = 0.40×humidity_norm + 0.35×rainfall_norm + 0.25×coldFactor
```

### Risk Seviyeleri

| Seviye | Eşik | Renk | Aksiyon |
|--------|------|------|---------|
| GÜVENLİ | < 0.20 | 🟢 Yeşil | İzleme |
| DİKKAT | 0.20–0.38 | 🟡 Sarı | Dikkatli İzleme |
| TEHLİKE | 0.38–0.55 | 🟠 Turuncu | Drone Sevkiyatı |
| KRİTİK | ≥ 0.55 | 🔴 Kırmızı | Acil Alarm + Drone |

---

## 🏗️ Sistem Mimarisi

```
┌─────────────────────────────────────────────────────────────────────┐
│                     ALGI KATMANI (Sensör Node)                      │
│  [BME280] ──I2C──┐                                                  │
│  [MPU-6050] ─I2C─┤─→ [FiPy MCU] ──LoRa──→ [LoRaWAN Gateway]      │
│                   │     (ESP32)              EU868 MHz               │
└───────────────────┴──────────────────────────────────────────────────┘
                                                         │ MQTT/HTTP
┌────────────────────────────────────────────────────────▼─────────────┐
│                        SUNUCU / MANTIK KATMANI                       │
│  [SimulationController]                                               │
│    ├── PoissonEngine (harita bazlı üretim)                           │
│    ├── RiskCalculator (R_rock, R_fog, R_slip)                        │
│    ├── WeatherService (Open-Meteo API)                               │
│    └── DroneDispatch (DANGER/CRITICAL → sevkiyat)                   │
└──────────────────────────────────────────────────────────────────────┘
                                                         │
┌────────────────────────────────────────────────────────▼─────────────┐
│                     KULLANICI ARAYÜZÜ (Swing + Web)                   │
│  [SwingMapPanel]  →  Harita, algılama çevreleri, drone animasyonu    │
│  [MapServer :8765] → Web harita arayüzü                              │
│  [NodeDetailDialog] → Poisson tablosu, enerji, LoRa detayları        │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 🧠 Ödev 2: Makine Öğrenmesi ile Trafik Sınıflandırma

Ödev 1'de sensörlerden toplanıp `.csv` formatında (veya UDP üzerinden canlı olarak) üretilen trafik paket özellikleri (paket boyutu, saniye başına byte, vb.), Ödev 2'de bir Makine Öğrenmesi (ML) modeli tarafından yorumlanır. Bu kısım, **"Yolun Beyni"** olarak işlev görür.

### Veri Seti ve Özellikler
Simülasyon ortamından `dataset.csv` adında **1596 kayıtlık** bir eğitim verisi üretilir. Kullanılan temel 4 özellik (feature):
1. **avg_size (byte):** 5 sn'lik penceredeki paketlerin ortalama boyutu (SAFE olaylar küçük, KAYA düşmesi büyüktür).
2. **bytes_per_sec:** Saniyede aktarılan bant genişliği yükü.
3. **jitter_ms:** Paketler arası gecikme dalgalanması.
4. **packet_count:** 5 saniyelik penceredeki toplam paket sayısı.

### Sınıflandırma Algoritmaları (Weka)
| Algoritma | Doğruluk | Makro F1 | Avantajları/Dezavantajları |
|-----------|----------|----------|----------------------------|
| **J48 (Karar Ağacı)** | **%79.1** | **0.777** | İnsan tarafından okunabilir kurallar üretir. **ROCK_FALL ve FOG ayrımında en iyisi.** |
| **Naive Bayes** | %74.9 | 0.708 | SAFE sınıfını kusursuz öğrenir (%100 Recall) ama FOG/ROCK karmaşasında zorlanır. |

*(Sistemlerin gerçekçi bir başarısını ölçmek için her iki algoritmada da **10-Fold Cross Validation** kullanılmıştır).*

---

## 🔗 Tam Sistem Akışı (İki Ödevin Bağlantısı)

Ödev 1 (Simülasyon) ve Ödev 2 (Makine Öğrenmesi) tam entegre çalışır:

1. **PC 1 (Veri Üreticisi):** Hava durumu bozulur veya ivmeölçer(MPU-6050) sarsıntı algılar. *Poisson süreci* lambda değerini fırlatır ve ağa yoğun/büyük UDP paketleri basılmaya başlar.
2. **Wireshark & Ağ Aktarımı:** Bu UDP paketleri gerçek ağ arayüzü üzerinden 9876 portuna uçar.
3. **PC 2 (Analiz Merkezi):** `Pcap4j` kütüphanesi portu dinler, gelen paketleri yakalar, özelliklerini çıkarır (avg_size, vb.) ve **önceden eğitilmiş J48 modeline** sokar.
4. **Sonuç & Alarm:** ML Modeli, saniyeler içinde "ROCK_FALL" olduğuna karar verir. Arayüzde alarm tetiklenir ve yol trafiğe kapatılır.

Bu sayede IoT cihazlarından çıkan ham byte'lar, ML ile anlamlı acil durum alarmlarına dönüşür.

---

## 🔧 Donanım Bileşenleri

### Pycom FiPy (Ana MCU)
- **İşlemci:** ESP32 Dual-Core 240 MHz
- **LoRa TX Akımı:** 120 mA @ +20 dBm
- **Sleep Akımı:** 10 µA
- **Batarya:** 3.7V LiPo 2000 mAh

### Bosch BME280 (Çevre Sensörü)
- **Sıcaklık:** -40~+85°C (±0.5°C)
- **Nem:** 0~100% RH (±3%)
- **Basınç:** 300~1100 hPa (±1 hPa)

### InvenSense MPU-6050 (IMU)
- **6-Eksen:** 3-Eksen İvmeölçer + 3-Eksen Jiroskop
- **Aralık:** ±2g / ±4g / ±8g / ±16g
- **Normal Akım:** 3.9 mA

---

## 🚀 Kurulum ve Çalıştırma

### Gereksinimler
- Java 11 veya üzeri
- İnternet bağlantısı (harita tile'ları + hava durumu API için)

### Windows

```batch
# Derleme
.\build.bat

# Çalıştırma
.\run.bat
```

### Linux/macOS

```bash
chmod +x build.sh run.sh
./build.sh
./run.sh
```

### Uygulama Açılınca
1. **Swing penceresi** açılır → İnteraktif harita (CartoDB Voyager/Dark)
2. **Simülasyonu Başlat** butonuna tıkla
3. 30-60 saniye bekle → Node'lar sararıp kırmızıya döner
4. **Drone üslerinden** drone'lar sorunlu bölgelere uçmaya başlar
5. Sol alt köşede **Poisson formülü** ve lambda değeri gösterilir

---

## 📁 Proje Yapısı

```
mersin-antalya/
├── src/main/
│   ├── model/
│   │   ├── SensorNode.java        # Sensör durumu, enerji, risk, olaylar
│   │   ├── RiskEvent.java         # Harita koordinatlı olay (x,y,d,r)
│   │   └── DroneUnit.java         # Drone konum/durum/rota yönetimi
│   ├── simulation/
│   │   ├── SimulationController.java  # Ana döngü + harita bazlı Poisson
│   │   ├── PoissonEngine.java         # Knuth algoritması, MapEvent
│   │   ├── RiskCalculator.java        # R_rock, R_fog, R_slip formülleri
│   │   └── WeatherService.java        # Open-Meteo API entegrasyonu
│   ├── ui/
│   │   ├── SwingMapPanel.java         # Harita çizimi (tile, node, drone)
│   │   ├── NodeDetailDialog.java      # Node detay penceresi
│   │   ├── MapServer.java             # HTTP sunucu :8765
│   │   └── MainFrame.java             # Ana pencere
│   └── util/
│       └── Constants.java             # Tüm sabitler ve GPS koordinatları
├── build.bat / build.sh           # Derleme scripti
├── run.bat / run.sh               # Çalıştırma scripti
├── generate_report.py             # Rapor oluşturucu (python-docx)
└── README.md                      # Bu dosya
```

---

## ✨ Özellikler

### Harita & Görselleştirme
- 🗺️ **CartoDB Slippy Map** — Gece/gündüz modunda gerçek harita tile'ları
- 🔵 **Algılama Çevreleri** — Her node'un yarıçapı haritada kesik daire olarak görünür
- 🌡️ **Isı Haritası Overlay** — Risk yoğunluğu renk gradyanı ile gösterilir
- 💫 **Pulse Animasyonu** — Kritik node'ların etrafında 3 kademeli yayılan halkalar

### Sensör & Fizik
- 🔬 **Gerçek Formüller** — Tüm risk hesapları akademik kaynaklara dayanır
- ⏱️ **Event TTL (30s)** — Olaylar 30 saniyede bozunur, doğal iyileşme simülasyonu
- 🔋 **Dead Node Koruması** — Batarya tükenince node otomatik offline
- ☀️ **Solar Şarj** — Gündüz saatlerinde güneş paneli şarj simülasyonu

### İletişim
- 📡 **LoRaWAN EU868** — SF7-SF12 adaptif Spreading Factor
- ⚖️ **%1 Duty Cycle** — ETSI EN 300 220 yasal uyumluluk
- 📊 **Animasyonlu LoRa** — Paket gönderimlerinde kesik çizgi animasyonu

### Drone Sistemi
- 🚁 **3 Otonom Drone** — DANGER/CRITICAL bölgelere otomatik sevkiyat
- 🗺️ **Harita Üzeri Uçuş** — Dönen rotor animasyonu + rota çizgisi
- 🎯 **Hedef Nişanı** — Kritik node üzerinde animasyonlu nişan halkası
- 📈 **Progress Bar** — Drone'un yüzde kaçının tamamlandığı görünür

### Hava Durumu
- 🌤️ **Open-Meteo API** — Gerçek zamanlı hava koşulları (fallback: simüle mod)
- ⚡ **Dinamik Lambda** — Hava koşullarına göre Poisson λ otomatik güncellenir
- 🌙 **Gece/Gündüz Modu** — Harita teması otomatik değişir

---

## 📊 Raporlar

Detaylı akademik raporu oluşturmak için:

```bash
python generate_report.py
```

Çıktılar:
- `rapor.docx` — Tam Word raporu (9 bölüm, 7 grafik, tablolar)
- `rapor_gorseller/` — PNG grafikler

---

## 📚 Referanslar

1. Pycom FiPy Datasheet — https://pycom.io/product/fipy/
2. Bosch BME280 Datasheet Rev. 1.6
3. LoRa Alliance, LoRaWAN® Spec v1.0.3
4. ETSI EN 300 220-2 V3.2.1 (Duty Cycle)
5. Open-Meteo API — https://open-meteo.com/
6. Kingman, J.F.C. (1993). Poisson Processes. Oxford University Press.

---

<div align="center">
  <b>Mersin-Antalya WSN — D400 Sahil Yolu Akıllı Risk Sistemi</b><br>
  Mayıs 2026
</div>
