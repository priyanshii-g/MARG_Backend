package in.marg.spatial;

import in.marg.model.GeoPoint;

/** Converts WGS84 geographic coordinates to the metric CRS used by MARG routing. */
public interface CoordinateProjection {
    MetricPoint forward(GeoPoint point);

    GeoPoint inverse(MetricPoint point);

    String crs();

    default double distanceMeters(GeoPoint a, GeoPoint b) {
        MetricPoint pa = forward(a);
        MetricPoint pb = forward(b);
        return Math.hypot(pb.x() - pa.x(), pb.y() - pa.y());
    }
}
