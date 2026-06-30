package main.ui;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import main.model.DroneUnit;
import main.model.RiskEvent;
import main.model.SensorNode;
import main.simulation.PoissonEngine;
import main.simulation.SimulationController;
import main.util.Constants;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lokal HTTP Server - Tam Ekran Harita Dashboard
 *
 * Tüm dashboard tarayıcı içinde çalışır:
 * - Leaflet.js harita (tam ekran arka plan)
 * - Sol panel: Kontroller (başlat/durdur, λ, hava)
 * - Sağ panel: Hava + İstatistikler + LoRa log
 * - Alt panel: Olay akışı
 * - Chart.js: Poisson grafiği
 * - REST API + Kontrol endpoint'leri
 */
public class MapServerPanel extends JPanel {

    private static final int PORT = 8765;
    private HttpServer server;
    private final SimulationController sim;

    private JLabel lblStatus;
    private JButton btnOpenMap;

    public MapServerPanel(SimulationController sim) {
        this.sim = sim;
        setBackground(new Color(10, 15, 30));
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        setPreferredSize(new Dimension(0, 50));
        buildUI();
        startServer();
    }

    private void buildUI() {
        lblStatus = new JLabel("🔴 Server başlatılıyor...");
        lblStatus.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblStatus.setForeground(new Color(255, 193, 7));

        btnOpenMap = new JButton("🗺️  Haritayı Aç  →  localhost:" + PORT);
        btnOpenMap.setBackground(new Color(0, 120, 100));
        btnOpenMap.setForeground(Color.WHITE);
        btnOpenMap.setFont(new Font("Segoe UI Emoji", Font.BOLD, 13));
        btnOpenMap.setFocusPainted(false);
        btnOpenMap.setBorderPainted(false);
        btnOpenMap.setOpaque(true);
        btnOpenMap.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnOpenMap.setEnabled(false);
        btnOpenMap.addActionListener(e -> openInBrowser());

        add(lblStatus);
        add(btnOpenMap);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                server = HttpServer.create(new InetSocketAddress(PORT), 50);
                server.createContext("/",           new MapHandler());
                server.createContext("/api/nodes",  new NodesApiHandler());
                server.createContext("/api/weather",new WeatherApiHandler());
                server.createContext("/api/events", new EventsApiHandler());
                server.createContext("/api/stats",  new StatsApiHandler());
                server.createContext("/api/control",new ControlApiHandler());
                server.createContext("/api/lora",   new LoraApiHandler());
                server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
                server.start();

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("🟢 Harita Aktif → localhost:" + PORT);
                    lblStatus.setForeground(new Color(76, 175, 80));
                    btnOpenMap.setEnabled(true);
                });

                Thread.sleep(800);
                openInBrowser();

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("❌ Port " + PORT + " kullanımda — manuel açın");
                    lblStatus.setForeground(new Color(244, 67, 54));
                    btnOpenMap.setEnabled(true);
                });
            }
        }, "HTTP-Server").start();
    }

    private void openInBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + PORT + "/"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Tarayıcı açılamadı.\nLütfen manuel açın: http://localhost:" + PORT,
                "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void stopServer() {
        if (server != null) server.stop(0);
    }

    // ================================================================
    // HTTP HANDLER'LAR
    // ================================================================

    private class MapHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            byte[] body = buildDashboardHTML().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.getResponseBody().close();
        }
    }

    private class NodesApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            JSONArray arr = new JSONArray();
            for (SensorNode n : sim.getNodes()) {
                JSONObject o = new JSONObject();
                o.put("id",          n.getId());
                o.put("nodeId",      n.getNodeId());
                o.put("lat",         n.getLatitude());
                o.put("lon",         n.getLongitude());
                o.put("type",        n.getType());
                o.put("location",    n.getLocation());
                o.put("active",      n.isActive());
                o.put("state",       n.getState().name());
                o.put("riskLevel",   n.getRiskLevel().name());
                o.put("riskColor",   n.getRiskLevel().color);
                o.put("rockRisk",    round(n.getRockRisk()));
                o.put("fogRisk",     round(n.getFogRisk()));
                o.put("slipRisk",    round(n.getSlipRisk()));
                o.put("overallRisk", round(n.getOverallRisk()));
                o.put("temperature", round1(n.getTemperature()));
                o.put("humidity",    round1(n.getHumidity()));
                o.put("pressure",    round1(n.getPressure()));
                o.put("windSpeed",   round1(n.getWindSpeed()));
                o.put("vibration",   n.getVibration());
                o.put("slopeAngle",  n.getSlopeAngle());
                o.put("batteryPct",  round1(n.getBatteryPercent()));
                o.put("batteryJ",    round1(n.getBatteryJoules()));
                o.put("consumedMah", round(n.getTotalConsumedMah()));
                o.put("loraPackets", n.getLoraPacketCount());
                o.put("loraRssi",    round1(n.getLoraRssi()));
                o.put("loraSf",      n.getLoraSf());
                o.put("totalEvents", n.getTotalEventCount());
                o.put("gateway",     Constants.GATEWAY_NAMES[n.getNearestGateway()]);
                if (n.getLastEvent() != null) {
                    o.put("lastEventType",  n.getLastEvent().getType().label);
                    o.put("lastEventEmoji", n.getLastEvent().getType().emoji);
                    o.put("lastEventTime",  n.getLastEvent().getFormattedTime());
                }
                arr.put(o);
            }
            sendJSON(ex, arr.toString());
        }
    }

    private class WeatherApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            var w = sim.getWeatherService();
            JSONObject o = new JSONObject();
            o.put("temperature",   w.getTemperature());
            o.put("humidity",      w.getHumidity());
            o.put("windSpeed",     w.getWindSpeed());
            o.put("pressure",      w.getPressure());
            o.put("precipitation", w.getPrecipitation());
            o.put("visibility",    w.getVisibility());
            o.put("condition",     w.getWeatherCondition());
            o.put("description",   w.getWeatherDescription());
            o.put("emoji",         w.getWeatherEmoji());
            o.put("isDaytime",     w.isDaytime());
            o.put("solarFactor",   w.getSolarChargeFactor());
            o.put("apiAvailable",  w.isApiAvailable());
            sendJSON(ex, o.toString());
        }
    }

    private class EventsApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            JSONArray arr = new JSONArray();
            int limit = 60;
            for (RiskEvent event : sim.getGlobalEventLog()) {
                if (limit-- <= 0) break;
                JSONObject o = new JSONObject();
                o.put("time",        event.getFormattedTime());
                o.put("nodeId",      event.getNodeLabel());
                o.put("location",    event.getLocation());
                o.put("type",        event.getType().label);
                o.put("emoji",       event.getType().emoji);
                o.put("color",       event.getType().color);
                o.put("lambda",      event.getPoissonLambda());
                o.put("k",           event.getPoissonK());
                o.put("probability", event.getPoissonProbability());
                o.put("magnitude",   event.getMagnitude());
                arr.put(o);
            }
            sendJSON(ex, arr.toString());
        }
    }

    private class StatsApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            JSONObject o = new JSONObject();
            o.put("running",         sim.isRunning());
            o.put("tickCount",       sim.getTickCount());
            o.put("activeNodes",     sim.getActiveNodeCount());
            o.put("totalNodes",      sim.getNodes().size());
            o.put("criticalNodes",   sim.getCriticalNodeCount());
            o.put("totalEvents",     sim.getTotalEventsGlobal());
            o.put("dronesDispatched",sim.getDronesDispatched());
            o.put("lambda",          sim.getPoissonEngine().getLambda());
            o.put("expectedValue",   sim.getPoissonEngine().getExpectedValue());
            o.put("variance",        sim.getPoissonEngine().getVariance());
            o.put("elapsedSeconds",  sim.getElapsedSeconds());

            // Poisson dağılım dizisi (grafik için)
            double[] dist = sim.getPoissonEngine().getPoissonDistribution(15);
            JSONArray distArr = new JSONArray();
            for (double v : dist) distArr.put(Math.round(v * 10000.0) / 10000.0);
            o.put("poissonDist", distArr);

            // Drone'lar
            JSONArray drones = new JSONArray();
            for (DroneUnit d : sim.getDrones()) {
                JSONObject dj = new JSONObject();
                dj.put("id",             d.getId());
                dj.put("droneId",        d.getDroneId());
                dj.put("state",          d.getState().name());
                dj.put("lat",            d.getCurrentLat());
                dj.put("lon",            d.getCurrentLon());
                dj.put("baseName",       d.getBaseName());
                dj.put("targetLocation", d.getTargetLocation() != null ? d.getTargetLocation() : "");
                dj.put("progress",       d.getRouteProgress());
                drones.put(dj);
            }
            o.put("drones", drones);
            sendJSON(ex, o.toString());
        }
    }

    /** Kontrol endpoint'i (POST /api/control) */
    private class ControlApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            // CORS preflight
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            String query = ex.getRequestURI().getQuery();
            String action = getParam(query, "action");
            String lambdaStr = getParam(query, "lambda");
            String weather = getParam(query, "weather");

            if ("start".equals(action)) {
                if (!sim.isRunning()) sim.start();
            } else if ("stop".equals(action)) {
                sim.stop();
            } else if ("reset".equals(action)) {
                sim.reset();
            } else if (lambdaStr != null) {
                try {
                    double lam = Double.parseDouble(lambdaStr);
                    sim.getPoissonEngine().setLambda(lam);
                } catch (NumberFormatException ignored) {}
            } else if (weather != null) {
                sim.getPoissonEngine().updateLambdaForWeather(weather);
            }

            sendJSON(ex, "{\"ok\":true}");
        }

        private String getParam(String query, String key) {
            if (query == null) return null;
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && kv[0].equals(key))
                    return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
            return null;
        }
    }

    /** LoRa log endpoint (son 100 mesaj) */
    private class LoraApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            JSONArray arr = new JSONArray();
            int limit = 80;
            for (RiskEvent event : sim.getGlobalEventLog()) {
                if (limit-- <= 0) break;
                if (event.getNodeLabel() != null) {
                    arr.put(event.toLoraLogString());
                }
            }
            sendJSON(ex, arr.toString());
        }
    }

    private void sendJSON(HttpExchange ex, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",  "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.getResponseBody().close();
    }

    private double round(double v)  { return Math.round(v * 1000.0) / 1000.0; }
    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    // ================================================================
    // TAM EKRAN HARİTA DASHBOARD HTML
    // ================================================================
    private String buildDashboardHTML() {

        StringBuilder gwJson = new StringBuilder("[");
        for (int i = 0; i < Constants.GATEWAY_COORDINATES.length; i++) {
            if (i > 0) gwJson.append(",");
            gwJson.append(String.format("{\"lat\":%.6f,\"lon\":%.6f,\"name\":\"%s\"}",
                Constants.GATEWAY_COORDINATES[i][0],
                Constants.GATEWAY_COORDINATES[i][1],
                Constants.GATEWAY_NAMES[i]));
        }
        gwJson.append("]");

        StringBuilder droneBasesJson = new StringBuilder("[");
        for (int i = 0; i < Constants.DRONE_BASE_COORDINATES.length; i++) {
            if (i > 0) droneBasesJson.append(",");
            droneBasesJson.append(String.format("{\"lat\":%.6f,\"lon\":%.6f,\"name\":\"%s\"}",
                Constants.DRONE_BASE_COORDINATES[i][0],
                Constants.DRONE_BASE_COORDINATES[i][1],
                Constants.DRONE_BASE_NAMES[i]));
        }
        droneBasesJson.append("]");

        return "<!DOCTYPE html>\n" +
"<html lang='tr'>\n" +
"<head>\n" +
"<meta charset='UTF-8'>\n" +
"<meta name='viewport' content='width=device-width,initial-scale=1'>\n" +
"<title>Mersin–Antalya WSN Risk Sistemi</title>\n" +
"<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>\n" +
"<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>\n" +
"<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>\n" +
"<style>\n" +
"*{margin:0;padding:0;box-sizing:border-box}\n" +
"html,body{height:100%;overflow:hidden;font-family:'Segoe UI',Arial,sans-serif;background:#050a14}\n" +
"#map{position:fixed;inset:0;z-index:0}\n" +

/* ── Üst başlık ── */
"#topbar{\n" +
"  position:fixed;top:0;left:0;right:0;z-index:1000;\n" +
"  height:48px;display:flex;align-items:center;justify-content:space-between;\n" +
"  padding:0 16px;\n" +
"  background:linear-gradient(90deg,rgba(5,10,30,.95),rgba(8,18,45,.95));\n" +
"  border-bottom:1px solid rgba(100,181,246,.25);\n" +
"  backdrop-filter:blur(10px);\n" +
"}\n" +
"#topbar-left{display:flex;align-items:center;gap:10px}\n" +
"#topbar-title{color:#64B5F6;font-size:13px;font-weight:700;white-space:nowrap}\n" +
"#topbar-sub{color:#546E7A;font-size:10px;white-space:nowrap}\n" +
"#topbar-right{display:flex;align-items:center;gap:8px}\n" +
".pill{\n" +
"  display:flex;align-items:center;gap:5px;\n" +
"  background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);\n" +
"  border-radius:20px;padding:3px 10px;font-size:11px;color:#aaa;\n" +
"}\n" +
".dot{width:7px;height:7px;border-radius:50%;flex-shrink:0}\n" +
".dot-green{background:#4CAF50;box-shadow:0 0 5px #4CAF50}\n" +
".dot-red{background:#F44336;box-shadow:0 0 5px #F44336;animation:blink 1s infinite}\n" +
".dot-yellow{background:#FFC107;box-shadow:0 0 5px #FFC107}\n" +
"@keyframes blink{0%,100%{opacity:1}50%{opacity:.3}}\n" +

/* ── Ortak panel stili (glassmorphism) ── */
".panel{\n" +
"  position:fixed;z-index:900;\n" +
"  background:rgba(6,12,28,.88);\n" +
"  border:1px solid rgba(100,181,246,.18);\n" +
"  border-radius:12px;\n" +
"  backdrop-filter:blur(14px);\n" +
"  box-shadow:0 8px 32px rgba(0,0,0,.6);\n" +
"  overflow:hidden;\n" +
"}\n" +
".panel.hidden{display:none}\n" +
".quick-menu{\n" +
"  position:fixed;top:58px;right:10px;z-index:1100;\n" +
"  display:flex;gap:6px;\n" +
"}\n" +
".quick-btn{\n" +
"  border:none;border-radius:18px;padding:6px 10px;\n" +
"  background:rgba(9,18,38,.92);color:#C8D8E8;\n" +
"  border:1px solid rgba(100,181,246,.28);\n" +
"  font-size:11px;font-weight:700;cursor:pointer;\n" +
"}\n" +
".quick-btn.active{background:rgba(100,181,246,.3);color:#fff}\n" +
".panel-header{\n" +
"  padding:8px 12px;\n" +
"  background:rgba(100,181,246,.08);\n" +
"  border-bottom:1px solid rgba(100,181,246,.15);\n" +
"  font-size:11px;font-weight:700;color:#64B5F6;\n" +
"  letter-spacing:.5px;text-transform:uppercase;\n" +
"}\n" +
".panel-body{padding:10px 12px}\n" +

/* ── Sol kontrol paneli ── */
"#left-panel{left:10px;top:58px;width:250px}\n" +

/* Simülasyon butonları */
".ctrl-btn{\n" +
"  width:100%;padding:7px;border:none;border-radius:7px;\n" +
"  font-size:12px;font-weight:700;cursor:pointer;\n" +
"  transition:filter .2s,transform .1s;margin-bottom:5px;\n" +
"}\n" +
".ctrl-btn:hover{filter:brightness(1.25)}\n" +
".ctrl-btn:active{transform:scale(.97)}\n" +
".btn-start{background:linear-gradient(135deg,#00897B,#00695C);color:#fff}\n" +
".btn-stop{background:linear-gradient(135deg,#C62828,#B71C1C);color:#fff}\n" +
".btn-reset{background:rgba(255,255,255,.08);color:#90A4AE;border:1px solid rgba(255,255,255,.12)}\n" +

/* Lambda slider */
"label.lbl{display:block;font-size:10px;color:#546E7A;margin:8px 0 3px}\n" +
"#lambda-val{color:#64B5F6;font-size:18px;font-weight:700;font-family:monospace;text-align:center;display:block}\n" +
"#lambda-math{color:#7986CB;font-size:10px;text-align:center;display:block;margin-bottom:4px}\n" +
"input[type=range]{width:100%;accent-color:#64B5F6;height:4px}\n" +
"select{\n" +
"  width:100%;background:#080f20;color:#ccc;\n" +
"  border:1px solid rgba(100,181,246,.3);border-radius:6px;\n" +
"  padding:5px 8px;font-size:12px;outline:none;cursor:pointer;\n" +
"}\n" +

/* ── Sağ istatistik paneli ── */
"#right-panel{right:10px;top:58px;width:280px;max-height:calc(100vh - 140px);overflow:auto}\n" +
".stat-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin-bottom:8px}\n" +
".stat-box{\n" +
"  background:rgba(0,0,0,.3);border-radius:8px;\n" +
"  padding:7px;text-align:center;\n" +
"  border:1px solid rgba(255,255,255,.06);\n" +
"}\n" +
".stat-val{font-size:20px;font-weight:700;font-family:monospace}\n" +
".stat-lbl{font-size:9px;color:#546E7A;margin-top:2px}\n" +
".w-row{display:flex;justify-content:space-between;padding:3px 0;font-size:12px}\n" +
".w-key{color:#455A64}.w-val{color:#90CAF9;font-family:monospace}\n" +
"hr.div{border:none;border-top:1px solid rgba(255,255,255,.07);margin:7px 0}\n" +

/* ── Alt veri merkezi (sekmeli) ── */
"#data-panel{\n" +
"  left:10px;right:300px;bottom:10px;height:220px;\n" +
"}\n" +
".data-tabs{display:flex;gap:6px;padding:8px 8px 0 8px}\n" +
".data-tab{\n" +
"  border:none;border-radius:8px;padding:6px 10px;\n" +
"  background:rgba(255,255,255,.08);color:#9FB3C8;\n" +
"  font-size:11px;font-weight:600;cursor:pointer;\n" +
"}\n" +
".data-tab.active{background:rgba(100,181,246,.25);color:#E3F2FD}\n" +
".data-content{padding:8px;height:170px}\n" +
".data-view{display:none;height:100%}\n" +
".data-view.active{display:block}\n" +
"#poissonChart{width:100%!important;height:150px!important}\n" +
"#lora-log{\n" +
"  height:150px;overflow-y:auto;font-family:monospace;\n" +
"  font-size:10px;line-height:1.5;\n" +
"}\n" +
"#lora-log::-webkit-scrollbar{width:4px}\n" +
"#lora-log::-webkit-scrollbar-thumb{background:#1e3a5f;border-radius:2px}\n" +

"#event-log{height:155px;overflow-y:auto;font-size:11px;line-height:1.6}\n" +
"#event-log::-webkit-scrollbar{width:4px}\n" +
"#event-log::-webkit-scrollbar-thumb{background:#2a1a50;border-radius:2px}\n" +
".ev-item{padding:2px 4px;border-radius:4px;border-left:3px solid #333;margin-bottom:3px}\n" +

"#drone-list{height:150px;overflow-y:auto;font-size:11px}\n" +
".drone-row{display:flex;align-items:center;gap:8px;padding:5px 4px;\n" +
"  border-bottom:1px solid rgba(255,255,255,.06);font-family:monospace}\n" +

/* ── Node marker animasyonları ── */
"@keyframes pulse-red{\n" +
"  0%{box-shadow:0 0 0 0 rgba(244,67,54,.9),0 0 8px rgba(244,67,54,.6)}\n" +
"  70%{box-shadow:0 0 0 14px rgba(244,67,54,0),0 0 18px rgba(244,67,54,.8)}\n" +
"  100%{box-shadow:0 0 0 0 rgba(244,67,54,0),0 0 8px rgba(244,67,54,.6)}\n" +
"}\n" +
"@keyframes tx-ring{\n" +
"  0%{box-shadow:0 0 0 0 rgba(100,181,246,.7)}\n" +
"  100%{box-shadow:0 0 0 10px rgba(100,181,246,0)}\n" +
"}\n" +
"@keyframes drone-bob{\n" +
"  0%,100%{transform:translateY(0) rotate(-8deg)}\n" +
"  50%{transform:translateY(-5px) rotate(8deg)}\n" +
"}\n" +
".nd{border-radius:50%;border:2px solid rgba(255,255,255,.55);cursor:pointer;transition:all .35s}\n" +
".nd-off{background:#252525!important;border-color:#333!important;opacity:.35!important}\n" +
".nd-crit{animation:pulse-red 1s infinite}\n" +
".nd-tx{animation:tx-ring .7s ease-out infinite}\n" +
".drone-fly{animation:drone-bob .75s ease-in-out infinite;font-size:22px}\n" +

/* ── Leaflet popup özelleştirme ── */
".leaflet-popup-content-wrapper{\n" +
"  background:rgba(6,12,28,.96);color:#ddd;\n" +
"  border:1px solid rgba(100,181,246,.3);border-radius:10px;\n" +
"  box-shadow:0 6px 30px rgba(0,0,0,.8);\n" +
"}\n" +
".leaflet-popup-tip{background:rgba(6,12,28,.96)}\n" +
".leaflet-popup-content{margin:12px 14px;font-family:monospace;font-size:11.5px}\n" +
".p-row{display:flex;justify-content:space-between;padding:2px 0}\n" +
".p-k{color:#455A64}.p-v{color:#90CAF9}\n" +
".p-bar{height:5px;border-radius:3px;margin:4px 0;transition:width .4s}\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +

/* ── HARİTA ── */
"<div id='map'></div>\n" +

/* ── ÜST BAŞLIK ── */
"<div id='topbar'>\n" +
"  <div id='topbar-left'>\n" +
"    <span style='font-size:22px'>🛡️</span>\n" +
"    <div>\n" +
"      <div id='topbar-title'>Mersin–Antalya Sahil Yolu — Akıllı Risk Erken Uyarı Sistemi</div>\n" +
"      <div id='topbar-sub'>Poisson Tabanlı WSN Digital Twin · LoRaWAN · Pycom FiPy · BME280 · MPU-6050 · Open-Meteo API</div>\n" +
"    </div>\n" +
"  </div>\n" +
"  <div id='topbar-right'>\n" +
"    <div class='pill'><div class='dot dot-green'></div><span>Server Aktif</span></div>\n" +
"    <div class='pill' id='sim-pill'><div class='dot dot-yellow' id='sim-dot'></div><span id='sim-lbl'>Bekliyor</span></div>\n" +
"    <div class='pill' id='clock'>⏱ 00:00:00</div>\n" +
"    <div class='pill' id='weather-pill'>🌍 Yükleniyor...</div>\n" +
"  </div>\n" +
"</div>\n" +
"<div class='quick-menu'>\n" +
"  <button id='btn-left' class='quick-btn' onclick='togglePanel(\"left-panel\",this)'>⚙️ Kontrol</button>\n" +
"  <button id='btn-right' class='quick-btn' onclick='togglePanel(\"right-panel\",this)'>📊 İstatistik</button>\n" +
"  <button id='btn-data' class='quick-btn active' onclick='togglePanel(\"data-panel\",this)'>🗂️ Veri</button>\n" +
"</div>\n" +

/* ── SOL KONTROL PANELİ ── */
"<div class='panel hidden' id='left-panel'>\n" +
"  <div class='panel-header'>⚙️ Simülasyon Kontrolü</div>\n" +
"  <div class='panel-body'>\n" +
"    <button class='ctrl-btn btn-start' onclick='ctrlAction(\"start\")'>▶  Simülasyonu Başlat</button>\n" +
"    <button class='ctrl-btn btn-stop'  onclick='ctrlAction(\"stop\")'>⏹  Durdur</button>\n" +
"    <button class='ctrl-btn btn-reset' onclick='ctrlAction(\"reset\")'>↺  Sıfırla</button>\n" +
"    <hr class='div'>\n" +
"    <div style='text-align:center;margin-bottom:4px'>\n" +
"      <small style='color:#7986CB;font-size:10px'>P(X=k) = e⁻λ · λᵏ / k!</small>\n" +
"    </div>\n" +
"    <span id='lambda-val'>λ = 0.50</span>\n" +
"    <span id='lambda-math'>E[X]=0.50  σ=0.71</span>\n" +
"    <div style='text-align:center;font-size:9.5px;color:#78909C;margin-top:6px;line-height:1.4'>\n" +
"      λ parametresi, sahil yolundaki gerçek zamanlı hava durumu API verileri (yağış, nem, görüş, rüzgar) kullanılarak sürekli ve otomatik olarak güncellenir.\n" +
"    </div>\n" +
"    <hr class='div'>\n" +
"    <div style='font-size:10px;color:#455A64'>\n" +
"      <div style='display:flex;gap:8px;flex-wrap:wrap'>\n" +
"        <span>⬛ Offline</span><span style='color:#4CAF50'>🟢 Güvenli</span>\n" +
"        <span style='color:#FFC107'>🟡 Dikkat</span><span style='color:#FF9800'>🟠 Tehlike</span>\n" +
"        <span style='color:#F44336'>🔴 Kritik</span>\n" +
"      </div>\n" +
"      <div style='margin-top:4px'>🛰️ Gateway &nbsp; 🏠 Drone Üssü</div>\n" +
"    </div>\n" +
"  </div>\n" +
"</div>\n" +

/* ── SAĞ İSTATİSTİK PANELİ ── */
"<div class='panel hidden' id='right-panel'>\n" +
"  <div class='panel-header'>📊 Sistem Durumu</div>\n" +
"  <div class='panel-body'>\n" +
"    <div class='stat-grid'>\n" +
"      <div class='stat-box'><div class='stat-val' id='s-active' style='color:#64B5F6'>0</div><div class='stat-lbl'>Aktif Node</div></div>\n" +
"      <div class='stat-box'><div class='stat-val' id='s-crit' style='color:#F44336'>0</div><div class='stat-lbl'>Kritik Alarm</div></div>\n" +
"      <div class='stat-box'><div class='stat-val' id='s-events' style='color:#FFC107'>0</div><div class='stat-lbl'>Toplam Olay</div></div>\n" +
"      <div class='stat-box'><div class='stat-val' id='s-drones' style='color:#81D4FA'>0</div><div class='stat-lbl'>Drone Görev</div></div>\n" +
"    </div>\n" +
"    <hr class='div'>\n" +
"    <div id='weather-pill-full' style='font-size:13px;font-weight:700;color:#FFD54F;margin-bottom:6px'>🌍 Yükleniyor...</div>\n" +
"    <div class='w-row'><span class='w-key'>🌡️ Sıcaklık</span><span class='w-val' id='w-temp'>--°C</span></div>\n" +
"    <div class='w-row'><span class='w-key'>💧 Nem</span><span class='w-val' id='w-hum'>--%</span></div>\n" +
"    <div class='w-row'><span class='w-key'>💨 Rüzgar</span><span class='w-val' id='w-wind'>-- km/h</span></div>\n" +
"    <div class='w-row'><span class='w-key'>⚡ Basınç</span><span class='w-val' id='w-pres'>-- hPa</span></div>\n" +
"    <div class='w-row'><span class='w-key'>🌧️ Yağış</span><span class='w-val' id='w-rain'>-- mm/h</span></div>\n" +
"    <div class='w-row'><span class='w-key'>👁️ Görüş</span><span class='w-val' id='w-vis'>-- km</span></div>\n" +
"    <div id='solar-lbl' style='font-size:10px;color:#FFD740;margin-top:5px;text-align:center'>☀️ Solar: Hesaplanıyor...</div>\n" +
"  </div>\n" +
"</div>\n" +

/* ── ALT VERİ MERKEZİ (SEKMELİ) ── */
"<div class='panel' id='data-panel'>\n" +
"  <div class='panel-header'>🗂️ Veri Merkezi</div>\n" +
"  <div class='data-tabs'>\n" +
"    <button class='data-tab active' onclick='switchDataTab(\"tab-chart\",this)'>📐 Poisson</button>\n" +
"    <button class='data-tab' onclick='switchDataTab(\"tab-lora\",this)'>📡 LoRa Log</button>\n" +
"    <button class='data-tab' onclick='switchDataTab(\"tab-event\",this)'>🚨 Olay Akışı</button>\n" +
"    <button class='data-tab' onclick='switchDataTab(\"tab-drone\",this)'>🚁 Drone</button>\n" +
"  </div>\n" +
"  <div class='data-content'>\n" +
"    <div id='tab-chart' class='data-view active'><canvas id='poissonChart'></canvas></div>\n" +
"    <div id='tab-lora' class='data-view'><div id='lora-log'></div></div>\n" +
"    <div id='tab-event' class='data-view'><div id='event-log'></div></div>\n" +
"    <div id='tab-drone' class='data-view'><div id='drone-list'></div></div>\n" +
"  </div>\n" +
"</div>\n" +

/* ── JAVASCRIPT ── */
"<script>\n" +
"// ─── HARİTA BAŞLATMA ───────────────────────────────────────────\n" +
"var map = L.map('map',{center:[36.3,33.0],zoom:10,zoomControl:false,preferCanvas:true});\n" +
"L.control.zoom({position:'bottomright'}).addTo(map);\n" +

"var darkTiles = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',{maxZoom:19});\n" +
"var lightTiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18});\n" +
"darkTiles.addTo(map);\n" +
"var isNight = true;\n" +

"var GW   = " + gwJson + ";\n" +
"var DBAS = " + droneBasesJson + ";\n" +

"// Gateway + Drone üsleri ekle\n" +
"GW.forEach(g=>L.marker([g.lat,g.lon],{icon:L.divIcon({html:'<div style=\"font-size:20px;filter:drop-shadow(0 0 5px #4FC3F7);cursor:default\">🛰️</div>',className:'',iconSize:[24,24],iconAnchor:[12,12]})}).bindTooltip('<b>'+g.name+'</b><br><small style=\"color:#4FC3F7\">LoRa Gateway</small>',{direction:'top'}).addTo(map));\n" +
"DBAS.forEach(b=>L.marker([b.lat,b.lon],{icon:L.divIcon({html:'<div style=\"font-size:17px;filter:drop-shadow(0 0 4px #81D4FA);cursor:default\">🏠</div>',className:'',iconSize:[20,20],iconAnchor:[10,10]})}).bindTooltip('<b>'+b.name+'</b>',{direction:'top'}).addTo(map));\n" +

"// ─── NODE VE DRONE MARKER'LARI ──────────────────────────────────\n" +
"var NM={}; // node markers\n" +
"var DM={}; // drone markers\n" +

"function makeNodeIcon(color,size,active,pulsing,tx){\n" +
"  var cls='nd'+(active?' nd-'+(pulsing?'crit':tx?'tx':''):'nd-off');\n" +
"  var shadow='0 0 10px '+color;\n" +
"  var html='<div class=\"'+cls+'\" style=\"width:'+size+'px;height:'+size+'px;background:'+color+';box-shadow:'+shadow+';\"></div>';\n" +
"  return L.divIcon({html,className:'',iconSize:[size,size],iconAnchor:[size/2,size/2],popupAnchor:[0,-size/2-4]});\n" +
"}\n" +

"function buildPopup(n){\n" +
"  if(!n.active) return '<b style=\"color:#64B5F6\">'+n.nodeId+'</b><br><span style=\"color:#555\">⚫ Offline — Başlatılmadı</span>';\n" +
"  var bc=n.batteryPct>60?'#4CAF50':n.batteryPct>30?'#FF9800':'#F44336';\n" +
"  var rPct=Math.round(n.overallRisk*100);\n" +
"  var last=n.lastEventEmoji?('<br>⚡ Son: '+n.lastEventEmoji+' '+n.lastEventType+' ['+n.lastEventTime+']'):'';\n" +
"  return '<div style=\"min-width:240px\">'+\n" +
"    '<b style=\"color:#64B5F6\">'+n.nodeId+'</b> <span style=\"color:#455A64;font-size:10px\">['+n.type+']</span><br>'+\n" +
"    '<span style=\"color:#78909C;font-size:10px\">📍 '+n.location+'</span>'+\n" +
"    '<hr style=\"border-color:#1a2a44;margin:5px 0\">'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🌡️ Sıcaklık</span><span class=\"p-v\">'+n.temperature+'°C</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">💧 Nem</span><span class=\"p-v\">'+n.humidity+'%</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">💨 Rüzgar</span><span class=\"p-v\">'+n.windSpeed+' km/h</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">⚡ Basınç</span><span class=\"p-v\">'+n.pressure+' hPa</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">📳 Titreşim</span><span class=\"p-v\">'+n.vibration.toFixed(3)+'g</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">📐 Eğim</span><span class=\"p-v\">'+n.slopeAngle+'°</span></div>'+\n" +
"    '<hr style=\"border-color:#1a2a44;margin:5px 0\">'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🪨 Kaya Riski</span><span class=\"p-v\">'+Math.round(n.rockRisk*100)+'%</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🌫️ Sis Riski</span><span class=\"p-v\">'+Math.round(n.fogRisk*100)+'%</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">⚠️ Kaygan</span><span class=\"p-v\">'+Math.round(n.slipRisk*100)+'%</span></div>'+\n" +
"    '<div style=\"background:#111;border-radius:3px;height:5px;margin:4px 0\"><div class=\"p-bar\" style=\"background:'+n.riskColor+';width:'+rPct+'%\"></div></div>'+\n" +
"    '<b style=\"color:'+n.riskColor+'\">● '+n.riskLevel+'</b>'+\n" +
"    '<hr style=\"border-color:#1a2a44;margin:5px 0\">'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🔋 Batarya</span><span class=\"p-v\" style=\"color:'+bc+'\">'+n.batteryPct+'% ('+n.batteryJ+' J)</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">📡 LoRa</span><span class=\"p-v\">SF'+n.loraSf+' | '+n.loraRssi+' dBm | '+n.loraPackets+' pkt</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🏗️ Gateway</span><span class=\"p-v\">'+n.gateway+'</span></div>'+\n" +
"    '<div class=\"p-row\"><span class=\"p-k\">🔢 Toplam Olay</span><span class=\"p-v\">'+n.totalEvents+'</span></div>'+\n" +
"    last+'</div>';\n" +
"}\n" +

"// ─── API POLLING ─────────────────────────────────────────────────\n" +
"function fetchAll(){\n" +
"  fetch('/api/nodes').then(r=>r.json()).then(updateNodes).catch(()=>{});\n" +
"  fetch('/api/weather').then(r=>r.json()).then(updateWeather).catch(()=>{});\n" +
"  fetch('/api/events').then(r=>r.json()).then(updateEvents).catch(()=>{});\n" +
"  fetch('/api/stats').then(r=>r.json()).then(updateStats).catch(()=>{});\n" +
"}\n" +

"function updateNodes(nodes){\n" +
"  nodes.forEach(n=>{\n" +
"    var sz=n.active?16:10;\n" +
"    var pulsing=n.active&&n.riskLevel==='CRITICAL';\n" +
"    var tx=n.active&&n.state==='TRANSMITTING';\n" +
"    var color=n.active?n.riskColor:'#2a2a2a';\n" +
"    var icon=makeNodeIcon(color,sz,n.active,pulsing,tx);\n" +
"    var popup=buildPopup(n);\n" +
"    if(NM[n.id]){\n" +
"      NM[n.id].setIcon(icon);\n" +
"      NM[n.id].setPopupContent(popup);\n" +
"    } else {\n" +
"      NM[n.id]=L.marker([n.lat,n.lon],{icon}).bindPopup(popup,{maxWidth:300}).addTo(map);\n" +
"    }\n" +
"  });\n" +
"}\n" +

"function updateWeather(w){\n" +
"  var d=w.emoji+' '+w.description;\n" +
"  document.getElementById('weather-pill').textContent=d;\n" +
"  document.getElementById('weather-pill-full').textContent=d;\n" +
"  document.getElementById('w-temp').textContent=w.temperature.toFixed(1)+'°C';\n" +
"  document.getElementById('w-hum').textContent=w.humidity.toFixed(0)+'%';\n" +
"  document.getElementById('w-wind').textContent=w.windSpeed.toFixed(1)+' km/h';\n" +
"  document.getElementById('w-pres').textContent=w.pressure.toFixed(0)+' hPa';\n" +
"  document.getElementById('w-rain').textContent=w.precipitation.toFixed(1)+' mm/h';\n" +
"  document.getElementById('w-vis').textContent=(w.visibility/1000).toFixed(1)+' km';\n" +
"  document.getElementById('solar-lbl').textContent=w.isDaytime?\n" +
"    '☀️ Solar Aktif: '+Math.round(w.solarFactor*100)+'% verimlilik':\n" +
"    '🌙 Gece: Solar kapalı, batarya tüketimde';\n" +
"  // gece/gündüz harita rengi\n" +
"  if(w.isDaytime&&isNight){map.removeLayer(darkTiles);lightTiles.addTo(map);isNight=false;}\n" +
"  else if(!w.isDaytime&&!isNight){map.removeLayer(lightTiles);darkTiles.addTo(map);isNight=true;}\n" +
"}\n" +

"var ecols={'Kaya Titreşimi':'#FF6B6B','Sis Yoğunlaşması':'#64B5F6',\n" +
"  'Kaygan Yol':'#FFEB3B','Mikro Heyelan':'#FF8A65',\n" +
"  'Şiddetli Rüzgar':'#80CBC4','Ani Yağış':'#4FC3F7','Düşük Görüş':'#CE93D8'};\n" +

"function updateEvents(events){\n" +
"  var el=document.getElementById('event-log');\n" +
"  el.innerHTML='';\n" +
"  events.slice(0,30).forEach(e=>{\n" +
"    var c=ecols[e.type]||'#aaa';\n" +
"    el.innerHTML+='<div class=\"ev-item\" style=\"border-color:'+c+';color:'+c+'\">'+\n" +
"      '<span style=\"color:#546E7A\">['+e.time+']</span> '+e.emoji+' <b>'+e.type+'</b>'+\n" +
"      ' <span style=\"color:#455A64;font-size:10px\">'+e.location+'</span>'+\n" +
"      '<br><span style=\"color:#37474F;font-size:10px\">λ='+e.lambda.toFixed(1)+' k='+e.k+\n" +
"      ' P='+e.probability.toFixed(4)+'</span></div>';\n" +
"  });\n" +
"}\n" +

"function updateStats(s){\n" +
"  // istatistikler\n" +
"  document.getElementById('s-active').textContent=s.activeNodes+'/'+s.totalNodes;\n" +
"  document.getElementById('s-crit').textContent=s.criticalNodes;\n" +
"  document.getElementById('s-events').textContent=s.totalEvents;\n" +
"  document.getElementById('s-drones').textContent=s.dronesDispatched;\n" +
"  // simülasyon durumu\n" +
"  var dot=document.getElementById('sim-dot');\n" +
"  var lbl=document.getElementById('sim-lbl');\n" +
"  dot.className='dot '+(s.running?'dot-green':'dot-yellow');\n" +
"  lbl.textContent=s.running?'Simülasyon Aktif':'Bekliyor';\n" +
"  // süre sayacı\n" +
"  var sec=s.elapsedSeconds;\n" +
"  var h=Math.floor(sec/3600),m=Math.floor((sec%3600)/60),ss=sec%60;\n" +
"  document.getElementById('clock').textContent='⏱ '+pad(h)+':'+pad(m)+':'+pad(ss);\n" +
"  // lambda UI\n" +
"  var lam=s.lambda;\n" +
"  document.getElementById('lambda-val').textContent='λ = '+lam.toFixed(2);\n" +
"  document.getElementById('lambda-math').textContent='E[X]='+lam.toFixed(2)+'  σ='+Math.sqrt(lam).toFixed(2);\n" +
"  // Poisson grafik\n" +
"  updatePoissonChart(s.poissonDist,lam);\n" +
"  // LoRa log (events'den yeniden oluştur)\n" +
"  updateLoraLog();\n" +
"  // Drone'lar\n" +
"  var dl=document.getElementById('drone-list');\n" +
"  dl.innerHTML='';\n" +
"  s.drones.forEach(d=>{\n" +
"    var flying=d.state!=='IDLE';\n" +
"    var col=flying?'#81D4FA':'#455A64';\n" +
"    dl.innerHTML+='<div class=\"drone-row\">'+\n" +
"      '<span '+(flying?'class=\"drone-fly\"':'style=\"font-size:17px\"')+'>🚁</span>'+\n" +
"      '<span style=\"color:'+col+'\">'+d.droneId+'</span>'+\n" +
"      '<span style=\"color:#37474F;font-size:10px\">'+(flying?'→ '+(d.targetLocation||d.state):d.baseName)+'</span>'+\n" +
"      '</div>';\n" +
"    if(flying){\n" +
"      var dIcon=L.divIcon({html:'<div class=\"drone-fly\">🚁</div>',className:'',iconSize:[28,28],iconAnchor:[14,14]});\n" +
"      if(DM[d.id]){DM[d.id].setLatLng([d.lat,d.lon]);DM[d.id].setIcon(dIcon);}\n" +
"      else{DM[d.id]=L.marker([d.lat,d.lon],{icon:dIcon,zIndexOffset:2000})\n" +
"        .bindTooltip(d.droneId+': '+(d.targetLocation||'Uçuşta'),{direction:'top'}).addTo(map);}\n" +
"    } else if(DM[d.id]){map.removeLayer(DM[d.id]);delete DM[d.id];}\n" +
"  });\n" +
"}\n" +

"function updateLoraLog(){\n" +
"  fetch('/api/events').then(r=>r.json()).then(events=>{\n" +
"    var ll=document.getElementById('lora-log');\n" +
"    ll.innerHTML='';\n" +
"    events.slice(0,50).forEach(e=>{\n" +
"      var isCrit=e.type.includes('Heyelan')||e.type.includes('Kaya');\n" +
"      var col=isCrit?'#F44336':e.type.includes('Sis')?'#64B5F6':'#00E676';\n" +
"      ll.innerHTML+='<div style=\"color:'+col+';padding:1px 0\">'+\n" +
"        '<span style=\"color:#37474F\">['+e.time+']</span> '+\n" +
"        e.nodeId+' → '+e.location+' | '+e.emoji+e.type+'</div>';\n" +
"    });\n" +
"  }).catch(()=>{});\n" +
"}\n" +

// Poisson Chart.js
"var poissonChart=null;\n" +
"function updatePoissonChart(dist,lam){\n" +
"  var labels=[],data=[];\n" +
"  dist.forEach((v,k)=>{ if(v>0.0005){labels.push(k);data.push(+(v*100).toFixed(2));} });\n" +
"  if(!poissonChart){\n" +
"    poissonChart=new Chart(document.getElementById('poissonChart'),{\n" +
"      type:'bar',\n" +
"      data:{labels,datasets:[{label:'P(X=k) %',data,\n" +
"        backgroundColor:'rgba(126,87,194,.7)',\n" +
"        borderColor:'rgba(186,104,200,1)',\n" +
"        borderWidth:1,borderRadius:4}]},\n" +
"      options:{responsive:false,animation:{duration:300},\n" +
"        plugins:{legend:{display:false},tooltip:{callbacks:{label:c=>c.parsed.y.toFixed(3)+'%'}}},\n" +
"        scales:{x:{ticks:{color:'#546E7A',font:{size:10}},grid:{color:'rgba(255,255,255,.05)'}},\n" +
"                y:{ticks:{color:'#546E7A',font:{size:10}},grid:{color:'rgba(255,255,255,.05)'}}}}\n" +
"    });\n" +
"  } else {\n" +
"    poissonChart.data.labels=labels;\n" +
"    poissonChart.data.datasets[0].data=data;\n" +
"    poissonChart.update('none');\n" +
"  }\n" +
"}\n" +

// Kontrol butonları
"function ctrlAction(a){fetch('/api/control?action='+a).catch(()=>{});}\n" +
"function onLambda(v){\n" +
"  var lam=v/10;\n" +
"  document.getElementById('lambda-val').textContent='λ = '+lam.toFixed(2);\n" +
"  document.getElementById('lambda-math').textContent='E[X]='+lam.toFixed(2)+'  σ='+Math.sqrt(lam).toFixed(2);\n" +
"  fetch('/api/control?lambda='+lam).catch(()=>{});\n" +
"}\n" +
"function onWeather(w){fetch('/api/control?weather='+w).catch(()=>{});}\n" +
"function pad(n){return String(n).padStart(2,'0')}\n" +
"function switchDataTab(id,btn){\n" +
"  document.querySelectorAll('.data-view').forEach(v=>v.classList.remove('active'));\n" +
"  document.querySelectorAll('.data-tab').forEach(t=>t.classList.remove('active'));\n" +
"  document.getElementById(id).classList.add('active');\n" +
"  btn.classList.add('active');\n" +
"}\n" +
"function togglePanel(id,btn){\n" +
"  var panel=document.getElementById(id);\n" +
"  var isHidden=panel.classList.contains('hidden');\n" +
"  if(isHidden){\n" +
"    panel.classList.remove('hidden');\n" +
"    btn.classList.add('active');\n" +
"  }else{\n" +
"    panel.classList.add('hidden');\n" +
"    btn.classList.remove('active');\n" +
"  }\n" +
"}\n" +

// Polling başlat
"fetchAll();\n" +
"setInterval(fetchAll,2000);\n" +
"</script>\n" +
"</body></html>";
    }
}
