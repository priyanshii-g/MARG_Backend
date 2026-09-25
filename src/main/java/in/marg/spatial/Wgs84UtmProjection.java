package in.marg.spatial;

import in.marg.model.GeoPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * WGS84 <-> UTM projection for local metric routing.
 * The Kaziranga–Karbi Anglong study region is in UTM zone 46N (EPSG:32646).
 * The implementation is self-contained so MARG does not need a GIS library merely
 * to perform the grid-distance calculations.
 */
@Service
public class Wgs84UtmProjection implements CoordinateProjection {
    private static final double WGS84_A = 6378137.0;
    private static final double WGS84_E2 = 0.0066943799901413165;
    private static final double UTM_K0 = 0.9996;

    private final int zone;
    private final double centralMeridianRad;
    private final String crs;

    public Wgs84UtmProjection(@Value("${marg.routing.utm-zone:46}") int zone) {
        if (zone < 1 || zone > 60) {
            throw new IllegalArgumentException("UTM zone must be between 1 and 60");
        }
        this.zone = zone;
        this.centralMeridianRad = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0);
        this.crs = "EPSG:" + (32600 + zone);
    }

    @Override
    public MetricPoint forward(GeoPoint point) {
        double lat = Math.toRadians(point.lat());
        double lon = Math.toRadians(point.lon());
        if (point.lat() < -80.0 || point.lat() > 84.0) {
            throw new IllegalArgumentException("UTM projection supports latitude approximately -80 to 84 degrees");
        }

        double eccPrimeSquared = WGS84_E2 / (1.0 - WGS84_E2);
        double sinLat = Math.sin(lat);
        double cosLat = Math.cos(lat);
        double tanLat = Math.tan(lat);
        double n = WGS84_A / Math.sqrt(1.0 - WGS84_E2 * sinLat * sinLat);
        double t = tanLat * tanLat;
        double c = eccPrimeSquared * cosLat * cosLat;
        double a = cosLat * normalizeDeltaLongitude(lon - centralMeridianRad);

        double e4 = WGS84_E2 * WGS84_E2;
        double e6 = e4 * WGS84_E2;
        double m = WGS84_A * ((1.0 - WGS84_E2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * lat
                - (3.0 * WGS84_E2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * Math.sin(2.0 * lat)
                + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * Math.sin(4.0 * lat)
                - (35.0 * e6 / 3072.0) * Math.sin(6.0 * lat));

        double x = UTM_K0 * n * (a
                + (1.0 - t + c) * Math.pow(a, 3) / 6.0
                + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * eccPrimeSquared) * Math.pow(a, 5) / 120.0)
                + 500000.0;

        double y = UTM_K0 * (m + n * tanLat * (a * a / 2.0
                + (5.0 - t + 9.0 * c + 4.0 * c * c) * Math.pow(a, 4) / 24.0
                + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * eccPrimeSquared) * Math.pow(a, 6) / 720.0));

        return new MetricPoint(x, y);
    }

    @Override
    public GeoPoint inverse(MetricPoint point) {
        double x = point.x() - 500000.0;
        double y = point.y();
        double eccPrimeSquared = WGS84_E2 / (1.0 - WGS84_E2);

        double e1 = (1.0 - Math.sqrt(1.0 - WGS84_E2)) / (1.0 + Math.sqrt(1.0 - WGS84_E2));
        double m = y / UTM_K0;
        double mu = m / (WGS84_A * (1.0 - WGS84_E2 / 4.0 - 3.0 * Math.pow(WGS84_E2, 2) / 64.0
                - 5.0 * Math.pow(WGS84_E2, 3) / 256.0));

        double phi1 = mu
                + (3.0 * e1 / 2.0 - 27.0 * Math.pow(e1, 3) / 32.0) * Math.sin(2.0 * mu)
                + (21.0 * e1 * e1 / 16.0 - 55.0 * Math.pow(e1, 4) / 32.0) * Math.sin(4.0 * mu)
                + (151.0 * Math.pow(e1, 3) / 96.0) * Math.sin(6.0 * mu)
                + (1097.0 * Math.pow(e1, 4) / 512.0) * Math.sin(8.0 * mu);

        double sinPhi1 = Math.sin(phi1);
        double cosPhi1 = Math.cos(phi1);
        double tanPhi1 = Math.tan(phi1);
        double n1 = WGS84_A / Math.sqrt(1.0 - WGS84_E2 * sinPhi1 * sinPhi1);
        double r1 = WGS84_A * (1.0 - WGS84_E2)
                / Math.pow(1.0 - WGS84_E2 * sinPhi1 * sinPhi1, 1.5);
        double t1 = tanPhi1 * tanPhi1;
        double c1 = eccPrimeSquared * cosPhi1 * cosPhi1;
        double d = x / (n1 * UTM_K0);

        double lat = phi1 - (n1 * tanPhi1 / r1)
                * (d * d / 2.0
                - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * eccPrimeSquared) * Math.pow(d, 4) / 24.0
                + (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * eccPrimeSquared - 3.0 * c1 * c1)
                * Math.pow(d, 6) / 720.0);

        double lon = centralMeridianRad + (d
                - (1.0 + 2.0 * t1 + c1) * Math.pow(d, 3) / 6.0
                + (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * eccPrimeSquared
                + 24.0 * t1 * t1) * Math.pow(d, 5) / 120.0) / cosPhi1;

        return new GeoPoint(Math.toDegrees(lat), Math.toDegrees(lon));
    }

    @Override
    public String crs() {
        return crs;
    }

    public int zone() {
        return zone;
    }

    private static double normalizeDeltaLongitude(double value) {
        while (value > Math.PI) value -= 2.0 * Math.PI;
        while (value < -Math.PI) value += 2.0 * Math.PI;
        return value;
    }
}
