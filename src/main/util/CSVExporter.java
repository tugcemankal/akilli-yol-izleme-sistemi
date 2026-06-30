package main.util;

import main.model.SensorNode;
import main.model.RiskEvent;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV Rapor Dışa Aktarma Yardımcısı
 */
public class CSVExporter {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Node durumlarını CSV'ye yaz
     */
    public static String exportNodeStatus(List<SensorNode> nodes) throws IOException {
        String filename = "node_status_" + LocalDateTime.now().format(FORMATTER) + ".csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            // Başlık
            pw.println("NodeID,Location,Type,Lat,Lon,Active,State," +
                       "Temperature_C,Humidity_pct,Pressure_hPa,Wind_kmh,Vibration_g," +
                       "RockRisk,FogRisk,SlipRisk,OverallRisk,RiskLevel," +
                       "Battery_pct,Battery_J,ConsumedMah,LoraPackets,RSSI_dBm,SF");

            for (SensorNode node : nodes) {
                pw.printf("%s,%s,%s,%.6f,%.6f,%s,%s," +
                         "%.2f,%.1f,%.1f,%.1f,%.4f," +
                         "%.4f,%.4f,%.4f,%.4f,%s," +
                         "%.2f,%.2f,%.4f,%d,%.1f,%d%n",
                    node.getNodeId(),
                    node.getLocation(),
                    node.getType(),
                    node.getLatitude(),
                    node.getLongitude(),
                    node.isActive() ? "YES" : "NO",
                    node.getState().name(),
                    node.getTemperature(),
                    node.getHumidity(),
                    node.getPressure(),
                    node.getWindSpeed(),
                    node.getVibration(),
                    node.getRockRisk(),
                    node.getFogRisk(),
                    node.getSlipRisk(),
                    node.getOverallRisk(),
                    node.getRiskLevel().label,
                    node.getBatteryPercent(),
                    node.getBatteryJoules(),
                    node.getTotalConsumedMah(),
                    node.getLoraPacketCount(),
                    node.getLoraRssi(),
                    node.getLoraSf()
                );
            }
        }
        return filename;
    }

    /**
     * Olay geçmişini CSV'ye yaz
     */
    public static String exportEvents(List<RiskEvent> events) throws IOException {
        String filename = "events_" + LocalDateTime.now().format(FORMATTER) + ".csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("EventID,Timestamp,NodeID,Location,EventType," +
                       "Temperature_C,Humidity_pct,Wind_kmh,Vibration_g," +
                       "PoissonLambda,PoissonK,PoissonProbability,Magnitude");

            for (RiskEvent event : events) {
                pw.printf("%s,%s,%s,%s,%s," +
                         "%.2f,%.1f,%.1f,%.4f," +
                         "%.4f,%d,%.6f,%.4f%n",
                    event.getEventId(),
                    event.getTimestamp(),
                    event.getNodeLabel(),
                    event.getLocation(),
                    event.getType().label,
                    event.getTemperature(),
                    event.getHumidity(),
                    event.getWindSpeed(),
                    event.getVibration(),
                    event.getPoissonLambda(),
                    event.getPoissonK(),
                    event.getPoissonProbability(),
                    event.getMagnitude()
                );
            }
        }
        return filename;
    }
}
