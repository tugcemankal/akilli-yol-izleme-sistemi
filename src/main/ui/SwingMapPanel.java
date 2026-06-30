package main.ui;

import main.model.DroneUnit;
import main.model.RiskEvent;
import main.model.SensorNode;
import main.simulation.SimulationController;
import main.util.Constants;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Swing Arayüzü İçin Tam İnteraktif Slippy Map (Harita) Bileşeni  v2.0
 *
 * YENİLİKLER:
 *  ✔ Çoklu Pulse Animasyonu  : Her kritik node'un etrafında üst üste 3 halka yayılır
 *  ✔ Glow / Radial Gradient  : Tehlikeli node'lar ışıl ışıl parlar
 *  ✔ Risk Heatmap Overlay    : Yoğun risk bölgelerini kırmızı/turuncu ısı haritası olarak gösterir
 *  ✔ Drone Rota Çizgisi      : Drone'un kalkış noktasından hedefe animasyonlu ok çizgisi
 *  ✔ Drone Canavar İkonu     : Tek kanat animasyonlu vektör drone çizimi
 *  ✔ Gece/Gündüz Harita      : CartoDB Dark/Voyager arası otomatik geçiş
 *  ✔ API Bağlantı Etiketi    : Sol üstte "Open-Meteo Connected" canlı durum
 *  ✔ Node enerji çubuğu      : Her node'un altında mini batarya bar
 */
public class SwingMapPanel extends JPanel
        implements MouseListener, MouseMotionListener, MouseWheelListener {

    private final SimulationController sim;

    // Harita parametreleri
    private double centerLat = Constants.MAP_CENTER_LAT;
    private double centerLon = Constants.MAP_CENTER_LON;
    private int zoom = Constants.MAP_DEFAULT_ZOOM;
    private final int minZoom = 7;
    private final int maxZoom = 15;

    // Mouse kontrol
    private Point  dragStartPoint = null;
    private double dragStartCenterLat;
    private double dragStartCenterLon;

    // Tile cache
    private final ConcurrentHashMap<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(4);

    // Animasyon fazları  (0-1, sürekli döngü)
    private float pulsePhase   = 0.0f;   // hız: 0.025 / frame
    private float glowPhase    = 0.0f;   // hız: 0.015 / frame
    private float droneRotation = 0.0f;  // drone kanat açısı

    // Hover
    private SensorNode hoveredNode   = null;
    private DroneUnit  hoveredDrone  = null;
    private double hoveredGateLat    = 0;
    private double hoveredGateLon    = 0;
    private String hoveredGateName   = "";

    // Animation timer (25 FPS ~ 40ms)
    private final Timer animationTimer;

    public SwingMapPanel(SimulationController sim) {
        this.sim = sim;
        setBackground(new Color(6, 12, 28));
        setDoubleBuffered(true);

        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);

        animationTimer = new Timer(40, e -> {
            pulsePhase  = (pulsePhase  + 0.025f) % 1.0f;
            glowPhase   = (glowPhase   + 0.015f) % 1.0f;
            droneRotation = (droneRotation + 4.0f) % 360.0f;
            repaint();
        });
        animationTimer.start();
    }

    // ============================================================
    // COORDINATE CONVERSIONS  (Web Mercator)
    // ============================================================
    private double lonToX(double lon, int z) {
        return (lon + 180.0) / 360.0 * Math.pow(2.0, z);
    }
    private double latToY(double lat, int z) {
        double latRad = Math.toRadians(lat);
        return (Math.PI - Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0)))
                / (2.0 * Math.PI) * Math.pow(2.0, z);
    }
    private double xToLon(double x, int z) {
        return (x / Math.pow(2.0, z)) * 360.0 - 180.0;
    }
    private double yToLat(double y, int z) {
        double mercator = Math.PI - (y / Math.pow(2.0, z)) * 2.0 * Math.PI;
        return Math.toDegrees(2.0 * Math.atan(Math.exp(mercator)) - Math.PI / 2.0);
    }
    private Point toScreen(double lat, double lon, double cx, double cy, int w, int h) {
        int sx = (int) (lonToX(lon, zoom) * 256.0 - cx + w / 2.0);
        int sy = (int) (latToY(lat, zoom) * 256.0 - cy + h / 2.0);
        return new Point(sx, sy);
    }

    // ============================================================
    // TILE SYSTEM
    // ============================================================
    private void requestTile(int z, int x, int y) {
        boolean isDay = sim.getWeatherService().isDaytime();
        String key = z + "_" + x + "_" + y + "_" + (isDay ? "day" : "night");
        if (tileCache.containsKey(key)) return;
        tileCache.put(key, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        tileLoader.submit(() -> {
            try {
                File cacheDir = new File("cache/tiles");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File cacheFile = new File(cacheDir, key + ".png");

                if (cacheFile.exists()) {
                    BufferedImage img = ImageIO.read(cacheFile);
                    if (img != null) {
                        tileCache.put(key, img);
                        SwingUtilities.invokeLater(this::repaint);
                        return;
                    }
                }

                String urlStr = isDay
                    ? String.format("https://a.basemaps.cartocdn.com/rastertiles/voyager/%d/%d/%d.png", z, x, y)
                    : String.format("https://a.basemaps.cartocdn.com/dark_all/%d/%d/%d.png", z, x, y);

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "MersinAntalyaRiskSystem/2.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream();
                         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        byte[] bytes = out.toByteArray();
                        try (FileOutputStream fos = new FileOutputStream(cacheFile)) { fos.write(bytes); }
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (img != null) { tileCache.put(key, img); SwingUtilities.invokeLater(this::repaint); }
                    }
                } else {
                    tileCache.remove(key);
                }
            } catch (Exception ex) {
                tileCache.remove(key);
            }
        });
    }

    // ============================================================
    // MAIN PAINT
    // ============================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth(), h = getHeight();
        double cx = lonToX(centerLon, zoom) * 256.0;
        double cy = latToY(centerLat, zoom) * 256.0;

        // 1. Harita Karoları
        drawTiles(g2, cx, cy, w, h);

        // 2. Heatmap Overlay
        drawHeatmap(g2, cx, cy, w, h);

        // 3. LoRa Bağlantı Çizgileri
        drawCommunicationLines(g2, cx, cy, w, h);

        // 4. Gateway'ler
        drawGateways(g2, cx, cy, w, h);

        // 5. Drone Üsleri
        drawDroneBases(g2, cx, cy, w, h);

        // 6. Sensor Nodes (glow + pulse + energy bar)
        drawSensorNodes(g2, cx, cy, w, h);

        // 7. Drone'lar (rota + ikon)
        drawDrones(g2, cx, cy, w, h);

        // 8. Hover Tooltip
        drawHoverTooltip(g2);

        // 9. Lejand + Bilgi Overlay
        drawMapLegend(g2, w, h);
        drawStatusOverlay(g2, w, h);
    }

    // ============================================================
    // 1. TILES
    // ============================================================
    private void drawTiles(Graphics2D g2, double cx, double cy, int w, int h) {
        boolean isDay = sim.getWeatherService().isDaytime();
        int maxTiles  = (1 << zoom);

        int minTX = (int) Math.floor((cx - w / 2.0) / 256.0);
        int maxTX = (int) Math.floor((cx + w / 2.0) / 256.0);
        int minTY = (int) Math.floor((cy - h / 2.0) / 256.0);
        int maxTY = (int) Math.floor((cy + h / 2.0) / 256.0);

        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                int wx = (tx % maxTiles + maxTiles) % maxTiles;
                if (ty < 0 || ty >= maxTiles) continue;
                int dx = (int) (tx * 256 - cx + w / 2.0);
                int dy = (int) (ty * 256 - cy + h / 2.0);
                String key = zoom + "_" + wx + "_" + ty + "_" + (isDay ? "day" : "night");
                BufferedImage img = tileCache.get(key);

                if (img != null && img.getWidth() > 1) {
                    g2.drawImage(img, dx, dy, null);
                } else {
                    requestTile(zoom, wx, ty);
                    g2.setColor(isDay ? new Color(240, 240, 245) : new Color(10, 16, 32));
                    g2.fillRect(dx, dy, 256, 256);
                    g2.setColor(isDay ? new Color(200, 200, 210, 40) : new Color(30, 45, 80, 50));
                    g2.drawRect(dx, dy, 256, 256);
                }
            }
        }
    }

    // ============================================================
    // 2. HEATMAP OVERLAY
    // ============================================================
    private void drawHeatmap(Graphics2D g2, double cx, double cy, int w, int h) {
        List<SensorNode> nodes = sim.getNodes();
        Composite original = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));

        for (SensorNode node : nodes) {
            if (!node.isActive()) continue;
            double risk = node.getOverallRisk();
            if (risk < 0.25) continue; // Düşük risk gösterme

            Point p = toScreen(node.getLatitude(), node.getLongitude(), cx, cy, w, h);

            // Risk seviyesine göre ısı rengi
            Color hotColor;
            float radius;
            if (node.getRiskLevel() == SensorNode.RiskLevel.CRITICAL) {
                hotColor = new Color(244, 67, 54);    // Kırmızı
                radius = 55 + (float)(glowPhase * 15);
            } else if (node.getRiskLevel() == SensorNode.RiskLevel.DANGER) {
                hotColor = new Color(255, 109, 0);    // Derin turuncu
                radius = 42;
            } else {
                hotColor = new Color(255, 235, 59);   // Sarı
                radius = 30;
            }

            // Radial gradient (ısı saçılımı)
            float[] fractions = {0.0f, 0.4f, 1.0f};
            Color[] colors = {
                new Color(hotColor.getRed(), hotColor.getGreen(), hotColor.getBlue(), 160),
                new Color(hotColor.getRed(), hotColor.getGreen(), hotColor.getBlue(), 60),
                new Color(hotColor.getRed(), hotColor.getGreen(), hotColor.getBlue(), 0)
            };
            RadialGradientPaint heat = new RadialGradientPaint(
                new Point2D.Float(p.x, p.y), radius, fractions, colors
            );
            g2.setPaint(heat);
            g2.fill(new Ellipse2D.Double(p.x - radius, p.y - radius, radius * 2, radius * 2));
        }
        g2.setComposite(original);
    }

    // ============================================================
    // 3. LoRa İLETİŞİM ÇİZGİLERİ
    // ============================================================
    private void drawCommunicationLines(Graphics2D g2, double cx, double cy, int w, int h) {
        for (SensorNode node : sim.getNodes()) {
            if (!node.isActive() || node.getState() != SensorNode.NodeState.TRANSMITTING) continue;

            Point pNode = toScreen(node.getLatitude(), node.getLongitude(), cx, cy, w, h);
            int gwIdx = node.getNearestGateway();
            double[] gw = Constants.GATEWAY_COORDINATES[gwIdx];
            Point pGw = toScreen(gw[0], gw[1], cx, cy, w, h);

            // Glow alt katman
            g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(100, 181, 246, 35));
            g2.draw(new Line2D.Double(pNode.x, pNode.y, pGw.x, pGw.y));

            // Animasyonlu kesik çizgi (veri akışı)
            float dashOffset = (float) ((sim.getTickCount() * 3) % 18);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[]{6.0f, 6.0f}, dashOffset));
            g2.setColor(new Color(100, 181, 246, 200));
            g2.draw(new Line2D.Double(pNode.x, pNode.y, pGw.x, pGw.y));
        }
    }

    // ============================================================
    // 4. GATEWAY'LER
    // ============================================================
    private void drawGateways(Graphics2D g2, double cx, double cy, int w, int h) {
        for (int i = 0; i < Constants.GATEWAY_COORDINATES.length; i++) {
            double[] gc = Constants.GATEWAY_COORDINATES[i];
            Point p = toScreen(gc[0], gc[1], cx, cy, w, h);

            // Dış halka
            g2.setColor(new Color(79, 195, 247, 50));
            g2.fill(new Ellipse2D.Double(p.x - 16, p.y - 16, 32, 32));
            // İç nokta
            g2.setColor(new Color(3, 169, 244));
            g2.fill(new Ellipse2D.Double(p.x - 6, p.y - 6, 12, 12));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(p.x - 6, p.y - 6, 12, 12));
            // Etiket
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.setColor(new Color(129, 212, 250));
            g2.drawString("🛰️ " + Constants.GATEWAY_NAMES[i], p.x + 10, p.y + 4);
        }
    }

    // ============================================================
    // 5. DRONE ÜSLERİ
    // ============================================================
    private void drawDroneBases(Graphics2D g2, double cx, double cy, int w, int h) {
        for (int i = 0; i < Constants.DRONE_BASE_COORDINATES.length; i++) {
            double[] dc = Constants.DRONE_BASE_COORDINATES[i];
            Point p = toScreen(dc[0], dc[1], cx, cy, w, h);

            g2.setColor(new Color(255, 179, 0, 55));
            g2.fill(new Ellipse2D.Double(p.x - 16, p.y - 16, 32, 32));
            g2.setColor(new Color(255, 143, 0));
            g2.fill(new Ellipse2D.Double(p.x - 7, p.y - 7, 14, 14));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Ellipse2D.Double(p.x - 7, p.y - 7, 14, 14));
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.setColor(new Color(255, 202, 40));
            g2.drawString("🏠 Üs", p.x - 18, p.y - 18);
        }
    }

    // ============================================================
    // 6. SENSOR NODES  (glow + çoklu pulse + enerji bar)
    // ============================================================
    private void drawSensorNodes(Graphics2D g2, double cx, double cy, int w, int h) {
        for (SensorNode node : sim.getNodes()) {
            Point p = toScreen(node.getLatitude(), node.getLongitude(), cx, cy, w, h);

            if (!node.isActive()) {
                // Offline: küçük gri nokta
                g2.setColor(new Color(60, 70, 80, 140));
                g2.fill(new Ellipse2D.Double(p.x - 4, p.y - 4, 8, 8));
                continue;
            }

            Color nodeColor;
            boolean isCritical = false;
            boolean isDanger   = false;

            switch (node.getRiskLevel()) {
                case CRITICAL:
                    nodeColor = new Color(244, 67, 54);
                    isCritical = true;
                    break;
                case DANGER:
                    nodeColor = new Color(255, 100, 0);
                    isDanger = true;
                    break;
                case CAUTION:
                    nodeColor = new Color(255, 220, 0);
                    break;
                default:
                    nodeColor = new Color(0, 230, 118);
                    break;
            }

            // ── ALGILAMA ÇEVRESİ (Hoca Yönergesi: d ≤ r) ───────────────────
            // Kesik daire ile her node'un kapsama alanı görselleştirilir.
            // Yarıçap: node tipine göre değişir (CLIFF_EDGE > COASTAL > TUNNEL)
            // Pixel karşılığı: zoom seviyesine göre 1° ≈ ~256*2^zoom/360 piksel
            // Basit yaklaşım: zoom=9 → 1° ≈ 145 piksel
            {
                // Normalize [0,100] uzayındaki r değerini piksel'e çevir
                // normalizeLon aralığı: 31.95–34.05 = 2.1° → 100 birim
                // Yani 1 birim = 2.1/100 = 0.021°
                // Ekrandaki piksel: 1° ≈ lonToX'den türetilen ölçek
                double scaleX = (lonToX(centerLon + 0.01, zoom) - lonToX(centerLon, zoom)) * 256.0 * 100;
                // r: CLIFF_EDGE=6.17, COASTAL=5.0, TUNNEL=3.57 (normalize birim)
                double normR = node.getType().equals("CLIFF_EDGE") ? 6.17
                             : node.getType().equals("TUNNEL")     ? 3.57 : 5.0;
                // normalize r → piksel: normR birimi × (piksel/birim)
                // 1 normalize birim = 2.1° / 100 = 0.021° → piksel
                double radiusPx = normR * Math.abs(scaleX) * 0.021;
                radiusPx = Math.max(12, Math.min(60, radiusPx)); // 12-60 piksel arası kliple

                // Renk: risk seviyesine göre
                Color circleColor;
                float alpha;
                if (isCritical) {
                    circleColor = new Color(244, 67, 54);
                    alpha = 0.55f;
                } else if (isDanger) {
                    circleColor = new Color(255, 120, 0);
                    alpha = 0.40f;
                } else if (node.getRiskLevel() == SensorNode.RiskLevel.CAUTION) {
                    circleColor = new Color(255, 220, 0);
                    alpha = 0.30f;
                } else {
                    circleColor = new Color(0, 200, 120);
                    alpha = 0.18f;
                }

                // İç dolgu (yarı saydam)
                Composite prev = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.35f));
                g2.setColor(circleColor);
                g2.fill(new Ellipse2D.Double(p.x - radiusPx, p.y - radiusPx, radiusPx * 2, radiusPx * 2));
                g2.setComposite(prev);

                // Kesik dış çevre
                float dashOff = (float)((sim.getTickCount() * 1.5) % 12);
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{5f, 4f}, dashOff));
                g2.setColor(new Color(circleColor.getRed(), circleColor.getGreen(),
                        circleColor.getBlue(), (int)(alpha * 255)));
                g2.draw(new Ellipse2D.Double(p.x - radiusPx, p.y - radiusPx, radiusPx * 2, radiusPx * 2));
                g2.setStroke(new BasicStroke(1.0f));
            }

            // ── GLOW EFFECT ─────────────────────────────────────────
            // Pin gövdesinin merkezi: p.y - 18 (pinH=28, pinBodyH=20 → merkez p.y-28+10 = p.y-18)
            int pinCY = p.y - 18;
            if (isCritical || isDanger) {
                float glowAlpha = 0.15f + (float) Math.abs(Math.sin(glowPhase * Math.PI)) * 0.25f;
                float glowR = isCritical ? 45 : 32;
                Color gc = isCritical ? new Color(244, 67, 54) : new Color(255, 100, 0);
                float[] fracs = {0.0f, 0.6f, 1.0f};
                Color[] cols = {
                    new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), (int)(glowAlpha * 255)),
                    new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), (int)(glowAlpha * 80)),
                    new Color(gc.getRed(), gc.getGreen(), gc.getBlue(), 0)
                };
                g2.setPaint(new RadialGradientPaint(new Point2D.Float(p.x, pinCY), glowR, fracs, cols));
                g2.fill(new Ellipse2D.Double(p.x - glowR, pinCY - glowR, glowR * 2, glowR * 2));
            }

            // ── ÜÇ KADEMELİ PULSE HALKALAR (Critical) ───────────────
            if (isCritical) {
                for (int ring = 0; ring < 3; ring++) {
                    float phase = (pulsePhase + ring * 0.33f) % 1.0f;
                    float radius = 12 + phase * 28;
                    int alpha = (int) ((1.0f - phase) * 200);
                    g2.setColor(new Color(244, 67, 54, alpha));
                    g2.setStroke(new BasicStroke(1.5f - phase));
                    g2.draw(new Ellipse2D.Double(p.x - radius, pinCY - radius, radius * 2, radius * 2));
                }
            }

            // ── TRANSMITTING HALKA ──────────────────────────────────
            if (node.getState() == SensorNode.NodeState.TRANSMITTING) {
                g2.setColor(new Color(100, 181, 246, 160));
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new Ellipse2D.Double(p.x - 10, pinCY - 10, 20, 20));
            }


            // ── MAP PIN (Balon) İKONU ────────────────────────────────
            // Resimde görüldüğü gibi: üstte daire gövde, altında sivri uç, içinde N## etiketi
            int pinW = 22;           // balon genişliği (piksel)
            int pinH = 28;           // balon yüksekliği (gövde + sivri uç)
            int pinBodyH = 20;       // daire gövde yüksekliği
            int pinPX = p.x;         // pin'in haritaya saplandığı x (uç noktası)
            int pinPY = p.y;         // pin'in haritaya saplandığı y (uç noktası)
            int pinLeft = pinPX - pinW / 2;
            int pinTop  = pinPY - pinH;  // gövde üst sol köşesi

            // ── Gölge ───────────────────────────────────────────────
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g2.setColor(Color.BLACK);
            g2.fillOval(pinLeft + 2, pinPY - 4, pinW - 2, 5);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

            // ── Sivri uç (üçgen) ────────────────────────────────────
            int[] tx = { pinLeft + pinW/2 - 5, pinLeft + pinW/2 + 5, pinPX };
            int[] ty = { pinTop + pinBodyH - 2, pinTop + pinBodyH - 2, pinPY };
            // Koyu kenar
            g2.setColor(nodeColor.darker().darker());
            g2.fillPolygon(tx, ty, 3);
            // Renkli üçgen
            g2.setColor(nodeColor);
            int[] tx2 = { pinLeft + pinW/2 - 4, pinLeft + pinW/2 + 4, pinPX };
            int[] ty2 = { pinTop + pinBodyH - 3, pinTop + pinBodyH - 3, pinPY - 1 };
            g2.fillPolygon(tx2, ty2, 3);

            // ── Daire gövde (dolgu) ──────────────────────────────────
            // Koyu kenar (halo)
            g2.setColor(nodeColor.darker().darker());
            g2.fillOval(pinLeft - 1, pinTop - 1, pinW + 2, pinBodyH + 2);
            // Ana renk
            g2.setColor(nodeColor);
            g2.fillOval(pinLeft, pinTop, pinW, pinBodyH);

            // ── İç parlama (highlight) ───────────────────────────────
            Color hiColor = isCritical
                ? new Color(255, 160, 160, 120)
                : new Color(255, 255, 255, 90);
            g2.setColor(hiColor);
            g2.fillOval(pinLeft + 4, pinTop + 3, pinW - 10, pinBodyH / 2 - 2);

            // ── Daire kenarlık ───────────────────────────────────────
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(new Color(255, 255, 255, 210));
            g2.drawOval(pinLeft, pinTop, pinW, pinBodyH);
            g2.setStroke(new BasicStroke(1.0f));

            // ── NODE ID ETİKETİ (balon içinde) ───────────────────────
            g2.setFont(new Font("SansSerif", Font.BOLD, 8));
            String label = String.format("N%02d", node.getId() + 1);
            FontMetrics fm = g2.getFontMetrics();
            int lx = pinPX - fm.stringWidth(label) / 2;
            int ly = pinTop + pinBodyH / 2 + fm.getAscent() / 2 - 1;
            // Gölgeli metin
            g2.setColor(new Color(0, 0, 0, 160));
            g2.drawString(label, lx + 1, ly + 1);
            // Beyaz metin
            g2.setColor(Color.WHITE);
            g2.drawString(label, lx, ly);

            // ── MİNİ ENERJİ ÇUBUĞU (pinin altında, harita üzerinde) ──
            int barW = pinW - 2, barH = 3;
            int barX = pinLeft + 1, barY = pinPY + 3;
            g2.setColor(new Color(20, 30, 50, 180));
            g2.fillRoundRect(barX, barY, barW, barH, 2, 2);
            double bat = node.getBatteryPercent() / 100.0;
            Color batCol = bat > 0.5 ? new Color(0, 200, 100) : (bat > 0.2 ? new Color(255, 165, 0) : new Color(220, 50, 50));
            g2.setColor(batCol);
            g2.fillRoundRect(barX, barY, (int)(barW * bat), barH, 2, 2);

        }
    }

    // ============================================================
    // 7. DRONE (vektör ikon + rota ok çizgisi)
    // ============================================================
    private void drawDrones(Graphics2D g2, double cx, double cy, int w, int h) {
        for (DroneUnit drone : sim.getDrones()) {
            if (drone.getState() == DroneUnit.DroneState.IDLE) continue;

            Point p = toScreen(drone.getCurrentLat(), drone.getCurrentLon(), cx, cy, w, h);

            // ── ROTA ÇİZGİSİ (Kalkış → Hedef) ─────────────────────
            if (drone.getTargetNodeId() >= 0 && drone.getTargetNodeId() < sim.getNodes().size()) {
                SensorNode target = sim.getNodes().get(drone.getTargetNodeId());
                Point pT = toScreen(target.getLatitude(), target.getLongitude(), cx, cy, w, h);

                // Kalkış noktasından çizgi (base)
                Point pBase = toScreen(
                    Constants.DRONE_BASE_COORDINATES[drone.getBaseIndex()][0],
                    Constants.DRONE_BASE_COORDINATES[drone.getBaseIndex()][1],
                    cx, cy, w, h
                );

                // Arka plan glow çizgi
                g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(0, 230, 200, 25));
                g2.draw(new Line2D.Double(pBase.x, pBase.y, pT.x, pT.y));

                // Animasyonlu kesik çizgi (rota)
                float dashOff = (float)((System.currentTimeMillis() / 50) % 16);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10.0f, new float[]{8.0f, 6.0f}, dashOff));
                g2.setColor(new Color(3, 218, 198, 180));
                g2.draw(new Line2D.Double(pBase.x, pBase.y, pT.x, pT.y));

                // Hedef üzerinde nişan halkası
                float aim = 8 + (float) Math.abs(Math.sin(glowPhase * Math.PI * 2)) * 5;
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(244, 67, 54, 180));
                g2.draw(new Ellipse2D.Double(pT.x - aim, pT.y - aim, aim * 2, aim * 2));
                g2.setColor(new Color(244, 67, 54, 100));
                g2.setStroke(new BasicStroke(0.8f));
                g2.draw(new Ellipse2D.Double(pT.x - 3, pT.y - 3, 6, 6));

                // Progress bar (üstten)
                int pbW = 50;
                int pbX = p.x - pbW / 2, pbY = p.y - 22;
                g2.setColor(new Color(10, 20, 40, 200));
                g2.fillRoundRect(pbX, pbY, pbW, 5, 3, 3);
                g2.setColor(new Color(3, 218, 198));
                g2.fillRoundRect(pbX, pbY, (int)(pbW * drone.getRouteProgress()), 5, 3, 3);
            }

            // ── DRONE VEKTÖRELİ İKONU ──────────────────────────────
            drawDroneIcon(g2, p.x, p.y, drone.getState() == DroneUnit.DroneState.INSPECTING);

            // ── DRONE ID ────────────────────────────────────────────
            g2.setFont(new Font("Monospaced", Font.BOLD, 9));
            g2.setColor(new Color(3, 218, 198));
            g2.drawString(drone.getDroneId(), p.x - 18, p.y - 25);
        }
    }

    /**
     * Vektörel drone ikonu: 4 pervane kollu, dönen rotorlar
     */
    private void drawDroneIcon(Graphics2D g2, int cx, int cy, boolean inspecting) {
        Color bodyColor    = new Color(3, 218, 198);
        Color rotorColor   = inspecting ? new Color(255, 200, 50) : new Color(200, 240, 255);
        float rotorAngle   = droneRotation;

        // Gövde merkez
        g2.setColor(new Color(8, 40, 60, 200));
        g2.fill(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
        g2.setColor(bodyColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Double(cx - 5, cy - 5, 10, 10));

        // 4 kol
        int[][] arms = {{-10, -10}, {10, -10}, {-10, 10}, {10, 10}};
        for (int[] arm : arms) {
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(3, 180, 160, 180));
            g2.draw(new Line2D.Double(cx, cy, cx + arm[0], cy + arm[1]));

            // Rotor dairesi
            g2.setColor(new Color(rotorColor.getRed(), rotorColor.getGreen(), rotorColor.getBlue(), 120));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(new Ellipse2D.Double(cx + arm[0] - 5, cy + arm[1] - 5, 10, 10));

            // Rotor bıçakları (dönen)
            AffineTransform at = g2.getTransform();
            g2.translate(cx + arm[0], cy + arm[1]);
            g2.rotate(Math.toRadians(arm[0] > 0 ? rotorAngle : -rotorAngle));
            g2.setColor(rotorColor);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(-4, 0, 4, 0));
            g2.setTransform(at);
        }

        // İnceleme lambaları
        if (inspecting) {
            float beamAlpha = 0.3f + (float) Math.abs(Math.sin(glowPhase * Math.PI * 3)) * 0.5f;
            g2.setColor(new Color(255, 220, 50, (int)(beamAlpha * 255)));
            g2.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        }
    }

    // ============================================================
    // 8. HOVER TOOLTIP
    // ============================================================
    private void drawHoverTooltip(Graphics2D g2) {
        if (hoveredNode != null)       drawNodeCard(g2, hoveredNode);
        else if (hoveredDrone != null) drawDroneCard(g2, hoveredDrone);
        else if (!hoveredGateName.isEmpty()) drawGatewayCard(g2, hoveredGateName, hoveredGateLat, hoveredGateLon);
    }

    private void drawNodeCard(Graphics2D g2, SensorNode n) {
        int x = 15, y = 15, w = 270, h = 345;

        g2.setColor(new Color(6, 12, 35, 240));
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        g2.setColor(new Color(100, 181, 246, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 14, 14));

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2.setColor(new Color(100, 181, 246));
        g2.drawString(n.getNodeId(), x + 15, y + 25);

        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(new Color(120, 140, 170));
        g2.drawString("[" + n.getType() + " · " + n.getLocation() + "]", x + 15, y + 40);

        // Risk seviye renk şeridi
        Color rColor;
        switch (n.getRiskLevel()) {
            case CRITICAL: rColor = new Color(244, 67, 54); break;
            case DANGER:   rColor = new Color(255, 100, 0); break;
            case CAUTION:  rColor = new Color(255, 220, 0); break;
            default:       rColor = new Color(0, 200, 100); break;
        }
        g2.setColor(rColor);
        g2.fillRoundRect(x, y, 4, h, 4, 4);

        g2.setColor(new Color(30, 45, 80, 80));
        g2.drawLine(x + 15, y + 50, x + w - 15, y + 50);

        if (!n.isActive()) {
            g2.setFont(new Font("Monospaced", Font.BOLD, 12));
            g2.setColor(new Color(140, 140, 140));
            g2.drawString("⚫ OFFLINE / UYKU MODU", x + 15, y + 75);
            return;
        }

        int curY = y + 68, sp = 18;
        drawInfoRow(g2, "🌡️ Sıcaklık",    String.format("%.1f °C", n.getTemperature()),   x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "💧 Nem",          String.format("%.0f %%", n.getHumidity()),      x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "💨 Rüzgar",       String.format("%.1f km/h", n.getWindSpeed()),   x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "⚡ Basınç",        String.format("%.1f hPa", n.getPressure()),     x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "📳 Titreşim",     String.format("%.3f g", n.getVibration()),      x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "📐 Eğim",         String.format("%.1f°", n.getSlopeAngle()),      x+15, curY, w-30);

        curY += 14;
        g2.setColor(new Color(30, 45, 80, 80));
        g2.drawLine(x + 15, curY, x + w - 15, curY);
        curY += 14;

        drawInfoRow(g2, "🪨 Kaya Riski",  String.format("%d%%", Math.round(n.getRockRisk()*100)),  x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "🌫️ Sis Riski",   String.format("%d%%", Math.round(n.getFogRisk()*100)),   x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "⚠️ Kayma Riski", String.format("%d%%", Math.round(n.getSlipRisk()*100)),  x+15, curY, w-30);

        curY += 12;
        int barH = 6;
        g2.setColor(new Color(20, 35, 60));
        g2.fillRoundRect(x + 15, curY, w - 30, barH, 3, 3);
        g2.setColor(rColor);
        g2.fillRoundRect(x + 15, curY, (int)(n.getOverallRisk() * (w - 30)), barH, 3, 3);

        curY += 16;
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(rColor);
        g2.drawString("● " + n.getRiskLevel().label, x + 15, curY);

        curY += 14;
        g2.setColor(new Color(30, 45, 80, 80));
        g2.drawLine(x + 15, curY, x + w - 15, curY);
        curY += 14;

        // Batarya + LoRa
        Color bCol = n.getBatteryPercent() > 50 ? new Color(0,200,100) : (n.getBatteryPercent() > 20 ? new Color(255,165,0) : new Color(220,50,50));
        drawInfoRow(g2, "🔋 Batarya",     String.format("%.1f%% (%.0f J)", n.getBatteryPercent(), n.getBatteryJoules()), x+15, curY, w-30, bCol); curY+=sp;
        drawInfoRow(g2, "📡 LoRa",        String.format("SF%d | RSSI: %.1f dBm", n.getLoraSf(), n.getLoraRssi()),       x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "🛰️ Gateway",     Constants.GATEWAY_NAMES[n.getNearestGateway()],                               x+15, curY, w-30);
    }

    private void drawDroneCard(Graphics2D g2, DroneUnit d) {
        int x = 15, y = 15, w = 270, h = 185;
        g2.setColor(new Color(6, 12, 35, 240));
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        g2.setColor(new Color(3, 218, 198, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 14, 14));

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2.setColor(new Color(3, 218, 198));
        g2.drawString("🚁 DRONE — " + d.getDroneId(), x + 15, y + 25);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(new Color(120, 140, 170));
        g2.drawString(d.getBaseName() + " Devriye Birimi", x + 15, y + 40);
        g2.setColor(new Color(30, 45, 80, 80));
        g2.drawLine(x + 15, y + 50, x + w - 15, y + 50);

        int curY = y + 68, sp = 18;
        Color stColor = d.getState() != DroneUnit.DroneState.IDLE ? new Color(3, 218, 198) : new Color(120, 140, 170);
        drawInfoRow(g2, "Durum",    d.getState().name(), x+15, curY, w-30, stColor); curY+=sp;
        drawInfoRow(g2, "Üs",       d.getBaseName(),     x+15, curY, w-30); curY+=sp;
        if (d.getState() != DroneUnit.DroneState.IDLE) {
            drawInfoRow(g2, "Hedef",  d.getTargetLocation() != null ? d.getTargetLocation() : "Üsse Dönüş", x+15, curY, w-30); curY+=sp;
            drawInfoRow(g2, "İlerleme", String.format("%d%%", Math.round(d.getRouteProgress()*100)), x+15, curY, w-30);
        } else {
            drawInfoRow(g2, "Görev", "Beklemede — Hazır", x+15, curY, w-30, new Color(76, 175, 80));
        }
    }

    private void drawGatewayCard(Graphics2D g2, String name, double lat, double lon) {
        int x = 15, y = 15, w = 270, h = 130;
        g2.setColor(new Color(6, 12, 35, 240));
        g2.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        g2.setColor(new Color(3, 169, 244, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Double(x, y, w, h, 14, 14));

        g2.setFont(new Font("Monospaced", Font.BOLD, 13));
        g2.setColor(new Color(3, 169, 244));
        g2.drawString("🛰️ LORAWAN GATEWAY", x + 15, y + 25);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(new Color(120, 140, 170));
        g2.drawString(name, x + 15, y + 40);
        g2.setColor(new Color(30, 45, 80, 80));
        g2.drawLine(x + 15, y + 50, x + w - 15, y + 50);

        int curY = y + 68, sp = 18;
        drawInfoRow(g2, "Frekans",   "EU868 MHz", x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "Protokol",  "LoRaWAN 1.0.3", x+15, curY, w-30); curY+=sp;
        drawInfoRow(g2, "Konum",     String.format("%.4f°N  %.4f°E", lat, lon), x+15, curY, w-30);
    }

    private void drawInfoRow(Graphics2D g2, String key, String val, int x, int y, int width) {
        drawInfoRow(g2, key, val, x, y, width, new Color(210, 225, 245));
    }
    private void drawInfoRow(Graphics2D g2, String key, String val, int x, int y, int width, Color valColor) {
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(130, 148, 172));
        g2.drawString(key, x, y);
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.setColor(valColor);
        int sw = g2.getFontMetrics().stringWidth(val);
        g2.drawString(val, x + width - sw, y);
    }

    // ============================================================
    // 9. LEJAND
    // ============================================================
    private void drawMapLegend(Graphics2D g2, int w, int h) {
        int x = w - 165, y = h - 215, lw = 150, lh = 200;

        g2.setColor(new Color(6, 12, 35, 215));
        g2.fill(new RoundRectangle2D.Double(x, y, lw, lh, 8, 8));
        g2.setColor(new Color(80, 150, 220, 50));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new RoundRectangle2D.Double(x, y, lw, lh, 8, 8));

        g2.setFont(new Font("Monospaced", Font.BOLD, 9));
        g2.setColor(new Color(100, 181, 246));
        g2.drawString("🔍 LEJAND", x + 8, y + 16);
        g2.setColor(new Color(30, 45, 80));
        g2.drawLine(x + 8, y + 22, x + lw - 8, y + 22);

        int curY = y + 36, ds = 16;
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        drawLegendDot(g2, "🟢 Güvenli",       new Color(0, 200, 100),  x+8, curY); curY+=ds;
        drawLegendDot(g2, "🟡 Dikkat",         new Color(255, 220, 0),  x+8, curY); curY+=ds;
        drawLegendDot(g2, "🟠 Tehlike",        new Color(255, 100, 0),  x+8, curY); curY+=ds;
        drawLegendDot(g2, "🔴 Kritik / Alarm", new Color(244, 67, 54),  x+8, curY); curY+=ds;
        drawLegendDot(g2, "⚫ Çevrimdışı",     new Color(70, 80, 90),   x+8, curY); curY+=ds;

        curY += 6;
        g2.setColor(new Color(30, 45, 80));
        g2.drawLine(x + 8, curY, x + lw - 8, curY);
        curY += 14;

        g2.setColor(new Color(190, 210, 240));
        g2.drawString("🛰️  LoRaWAN Gateway", x + 8, curY); curY+=ds;
        g2.drawString("🏠  Drone Üssü",       x + 8, curY); curY+=ds;
        g2.drawString("🚁  Devriye Drone",     x + 8, curY); curY+=ds;
        g2.setColor(new Color(100, 181, 246, 180));
        g2.drawString("≈   Isı Haritası",     x + 8, curY);
    }

    private void drawLegendDot(Graphics2D g2, String label, Color c, int x, int y) {
        g2.setColor(c);
        g2.fill(new Ellipse2D.Double(x, y - 7, 7, 7));
        g2.setColor(new Color(255,255,255,100));
        g2.draw(new Ellipse2D.Double(x, y - 7, 7, 7));
        g2.setColor(new Color(195, 215, 245));
        g2.drawString(label, x + 14, y);
    }

    // ============================================================
    // 10. STATUS OVERLAY (sol alt — gece/gündüz modu + API durumu)
    // ============================================================
    private void drawStatusOverlay(Graphics2D g2, int w, int h) {
        boolean isDay = sim.getWeatherService().isDaytime();
        boolean apiOk = sim.getWeatherService().isApiAvailable();

        // Sol üst köşe: mod + api bant
        int sx = 12, sy = 12, sw = 220, sh = 44;
        g2.setColor(new Color(6, 12, 35, 200));
        g2.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, 8, 8));
        g2.setColor(new Color(60, 100, 160, 60));
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new RoundRectangle2D.Double(sx, sy, sw, sh, 8, 8));

        // Mod ikonu
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        g2.drawString(isDay ? "☀️" : "🌙", sx + 10, sy + 24);

        // Mod metni
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.setColor(isDay ? new Color(255, 210, 80) : new Color(130, 160, 220));
        g2.drawString(isDay ? "GÜNDÜZ MODU" : "GECE MODU", sx + 32, sy + 18);

        // API durumu
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(apiOk ? new Color(0, 200, 100) : new Color(255, 180, 0));
        g2.drawString(apiOk ? "● Open-Meteo API Bağlı" : "● Simüle Mod", sx + 32, sy + 33);

        // Sol alt: λ ve Poisson formülü
        int lx = 12, ly = h - 48, lw2 = 240, lh2 = 38;
        g2.setColor(new Color(6, 12, 35, 200));
        g2.fill(new RoundRectangle2D.Double(lx, ly, lw2, lh2, 8, 8));
        g2.setColor(new Color(60, 100, 160, 60));
        g2.draw(new RoundRectangle2D.Double(lx, ly, lw2, lh2, 8, 8));

        double lambda = sim.getPoissonEngine().getLambda();
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(new Color(206, 147, 216));
        g2.drawString(String.format("P(X=k) = e\u207b\u03bb \u00b7 \u03bb\u1d4f / k!    \u03bb = %.2f", lambda), lx + 10, ly + 17);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(new Color(100, 120, 150));
        g2.drawString(String.format("E[X] = %.2f   \u03c3 = %.2f   (Poisson Süreci)", lambda, Math.sqrt(lambda)), lx + 10, ly + 31);
    }

    // ============================================================
    // MOUSE LISTENERS
    // ============================================================
    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            dragStartPoint     = e.getPoint();
            dragStartCenterLat = centerLat;
            dragStartCenterLon = centerLon;
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
    }
    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            dragStartPoint = null;
            setCursor(Cursor.getDefaultCursor());
        }
    }
    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragStartPoint == null) return;
        double dx = e.getX() - dragStartPoint.x;
        double dy = e.getY() - dragStartPoint.y;
        double cxS = lonToX(dragStartCenterLon, zoom) * 256.0;
        double cyS = latToY(dragStartCenterLat, zoom) * 256.0;
        centerLon = xToLon((cxS - dx) / 256.0, zoom);
        centerLat = yToLat((cyS - dy) / 256.0, zoom);
        repaint();
    }
    @Override
    public void mouseMoved(MouseEvent e) {
        int w = getWidth(), h = getHeight();
        double cx = lonToX(centerLon, zoom) * 256.0;
        double cy = latToY(centerLat, zoom) * 256.0;

        SensorNode bestNode = null; double minND = Double.MAX_VALUE;
        for (SensorNode n : sim.getNodes()) {
            Point p = toScreen(n.getLatitude(), n.getLongitude(), cx, cy, w, h);
            double d = p.distance(e.getPoint());
            if (d < 12 && d < minND) { minND = d; bestNode = n; }
        }

        DroneUnit bestDrone = null; double minDD = Double.MAX_VALUE;
        if (bestNode == null) {
            for (DroneUnit dr : sim.getDrones()) {
                if (dr.getState() == DroneUnit.DroneState.IDLE) continue;
                Point p = toScreen(dr.getCurrentLat(), dr.getCurrentLon(), cx, cy, w, h);
                double d = p.distance(e.getPoint());
                if (d < 16 && d < minDD) { minDD = d; bestDrone = dr; }
            }
        }

        String gateName = ""; double gateLat = 0, gateLon = 0; double minGD = Double.MAX_VALUE;
        if (bestNode == null && bestDrone == null) {
            for (int i = 0; i < Constants.GATEWAY_COORDINATES.length; i++) {
                double[] gw = Constants.GATEWAY_COORDINATES[i];
                Point p = toScreen(gw[0], gw[1], cx, cy, w, h);
                double d = p.distance(e.getPoint());
                if (d < 15 && d < minGD) { minGD = d; gateName = Constants.GATEWAY_NAMES[i]; gateLat = gw[0]; gateLon = gw[1]; }
            }
        }

        boolean changed = (bestNode != hoveredNode) || (bestDrone != hoveredDrone) || (!gateName.equals(hoveredGateName));
        hoveredNode = bestNode; hoveredDrone = bestDrone;
        hoveredGateName = gateName; hoveredGateLat = gateLat; hoveredGateLon = gateLon;
        if (changed) repaint();
    }
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int newZoom = Math.max(minZoom, Math.min(maxZoom, zoom + (e.getWheelRotation() < 0 ? 1 : -1)));
        if (newZoom == zoom) return;
        int w = getWidth(), h = getHeight();
        double cxO = lonToX(centerLon, zoom) * 256.0;
        double cyO = latToY(centerLat, zoom) * 256.0;
        double mMx = cxO + (e.getX() - w / 2.0);
        double mMy = cyO + (e.getY() - h / 2.0);
        double fX  = mMx / (256.0 * Math.pow(2.0, zoom));
        double fY  = mMy / (256.0 * Math.pow(2.0, zoom));
        zoom = newZoom;
        double cxN = fX * (256.0 * Math.pow(2.0, zoom)) - (e.getX() - w / 2.0);
        double cyN = fY * (256.0 * Math.pow(2.0, zoom)) - (e.getY() - h / 2.0);
        centerLon = xToLon(cxN / 256.0, zoom);
        centerLat = yToLat(cyN / 256.0, zoom);
        repaint();
    }
    @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            // Çift tıkla en yakın node'u bul ve detay penceresini aç
            int w = getWidth(), h = getHeight();
            double cx = lonToX(centerLon, zoom) * 256.0;
            double cy = latToY(centerLat, zoom) * 256.0;

            SensorNode closest = null;
            double minD = Double.MAX_VALUE;
            for (SensorNode n : sim.getNodes()) {
                if (!n.isActive()) continue;
                Point p = toScreen(n.getLatitude(), n.getLongitude(), cx, cy, w, h);
                double d = p.distance(e.getPoint());
                if (d < 30 && d < minD) { minD = d; closest = n; }
            }

            if (closest != null) {
                // Ana pencereyi bul
                Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
                NodeDetailDialog dlg = new NodeDetailDialog(parentFrame, closest, sim);
                dlg.setVisible(true);
            } else {
                centerMap(); // Haritayı merkeze al
            }
        }
    }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {
        hoveredNode = null; hoveredDrone = null; hoveredGateName = "";
        repaint();
    }

    // ============================================================
    // UTILITY
    // ============================================================
    public void focusOnNode(SensorNode node) {
        if (node == null) return;
        centerLat = node.getLatitude();
        centerLon = node.getLongitude();
        zoom = 12;
        repaint();
    }
    public void centerMap() {
        centerLat = Constants.MAP_CENTER_LAT;
        centerLon = Constants.MAP_CENTER_LON;
        zoom = Constants.MAP_DEFAULT_ZOOM;
        repaint();
    }
}
