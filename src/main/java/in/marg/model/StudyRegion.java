package in.marg.model;

public record StudyRegion(
        String name,
        double minLat,
        double minLon,
        double maxLat,
        double maxLon,
        GeoPoint origin,
        GeoPoint destination
) {
    public boolean contains(GeoPoint point) {
        return point.lat() >= minLat && point.lat() <= maxLat
                && point.lon() >= minLon && point.lon() <= maxLon;
    }
}
