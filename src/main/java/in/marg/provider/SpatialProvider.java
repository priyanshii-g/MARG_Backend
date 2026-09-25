package in.marg.provider;

import in.marg.model.GeoPoint;

/** Base contract for point-sampled spatial data sources used by MARG. */
public interface SpatialProvider<T> {
    T sample(GeoPoint point);

    /** Stable identifier for provenance and diagnostics, e.g. bhuvan:lulc-50k-2015-16. */
    String providerId();
}
