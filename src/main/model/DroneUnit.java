package main.model;

/**
 * Devriye Dronu Modeli
 */
public class DroneUnit {

    public enum DroneState {
        IDLE,       // Üste bekliyor
        LAUNCHING,  // Kalkıyor
        EN_ROUTE,   // Hedefe gidiyor
        INSPECTING, // Bölgeyi inceliyor
        RETURNING   // Üsse dönüyor
    }

    private final int id;
    private final String droneId;
    private final int baseIndex;     // Hangi üste bağlı
    private final String baseName;

    private DroneState state;
    private double currentLat;
    private double currentLon;
    private double targetLat;
    private double targetLon;
    private double homeLat;
    private double homeLon;

    private int targetNodeId;        // Hangi node'a gidiyor
    private String targetLocation;

    private double speedKmH = 80.0;
    private int inspectionTimeLeft;  // saniye

    // Animasyon için interpolasyon parametreleri
    private double routeProgress;    // 0.0 - 1.0
    private double startLat, startLon;

    public DroneUnit(int id, int baseIndex) {
        this.id = id;
        this.droneId = String.format("DRONE-%02d", id + 1);
        this.baseIndex = baseIndex;
        this.baseName = main.util.Constants.DRONE_BASE_NAMES[baseIndex];
        this.homeLat = main.util.Constants.DRONE_BASE_COORDINATES[baseIndex][0];
        this.homeLon = main.util.Constants.DRONE_BASE_COORDINATES[baseIndex][1];
        this.currentLat = homeLat;
        this.currentLon = homeLon;
        this.state = DroneState.IDLE;
        this.routeProgress = 0.0;
    }

    public void dispatchTo(SensorNode node) {
        if (state != DroneState.IDLE) return;

        this.targetNodeId = node.getId();
        this.targetLocation = node.getLocation();
        this.targetLat = node.getLatitude();
        this.targetLon = node.getLongitude();
        this.startLat = currentLat;
        this.startLon = currentLon;
        this.routeProgress = 0.0;
        this.state = DroneState.EN_ROUTE;
        this.inspectionTimeLeft = main.util.Constants.DRONE_INSPECTION_TIME_S;
    }

    /**
     * Drone konumunu güncelle
     * @param deltaSeconds geçen süre
     * @return true eğer hedefe ulaştıysa
     */
    public boolean updatePosition(double deltaSeconds) {
        if (state == DroneState.IDLE || state == DroneState.INSPECTING) {
            return false;
        }

        if (state == DroneState.EN_ROUTE) {
            // Mesafeyi hesapla (km)
            double dlat = targetLat - startLat;
            double dlon = targetLon - startLon;
            double distKm = Math.sqrt(dlat * dlat + dlon * dlon) * 111.0;

            // Progress artır
            double progressPerSecond = speedKmH / (distKm * 3600);
            routeProgress = Math.min(1.0, routeProgress + progressPerSecond * deltaSeconds);

            // Konumu interpolasyon ile güncelle
            currentLat = startLat + (targetLat - startLat) * routeProgress;
            currentLon = startLon + (targetLon - startLon) * routeProgress;

            if (routeProgress >= 1.0) {
                state = DroneState.INSPECTING;
                return true;
            }
        } else if (state == DroneState.RETURNING) {
            double dlat = homeLat - startLat;
            double dlon = homeLon - startLon;
            double distKm = Math.sqrt(dlat * dlat + dlon * dlon) * 111.0;

            double progressPerSecond = speedKmH / (distKm * 3600 + 0.001);
            routeProgress = Math.min(1.0, routeProgress + progressPerSecond * deltaSeconds);

            currentLat = startLat + (homeLat - startLat) * routeProgress;
            currentLon = startLon + (homeLon - startLon) * routeProgress;

            if (routeProgress >= 1.0) {
                currentLat = homeLat;
                currentLon = homeLon;
                state = DroneState.IDLE;
            }
        }

        return false;
    }

    public void tickInspection(double deltaSeconds) {
        if (state == DroneState.INSPECTING) {
            inspectionTimeLeft -= (int) deltaSeconds;
            if (inspectionTimeLeft <= 0) {
                startLat = currentLat;
                startLon = currentLon;
                routeProgress = 0.0;
                state = DroneState.RETURNING;
            }
        }
    }

    public boolean isAvailable() { return state == DroneState.IDLE; }

    // Getters
    public int getId() { return id; }
    public String getDroneId() { return droneId; }
    public int getBaseIndex() { return baseIndex; }
    public String getBaseName() { return baseName; }
    public DroneState getState() { return state; }
    public double getCurrentLat() { return currentLat; }
    public double getCurrentLon() { return currentLon; }
    public double getTargetLat() { return targetLat; }
    public double getTargetLon() { return targetLon; }
    public String getTargetLocation() { return targetLocation; }
    public int getTargetNodeId() { return targetNodeId; }
    public double getRouteProgress() { return routeProgress; }
    public int getInspectionTimeLeft() { return inspectionTimeLeft; }
}
