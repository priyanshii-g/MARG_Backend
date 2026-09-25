package in.marg.floodraster;

import in.marg.flood.FloodHazardClass;
import in.marg.model.GeoPoint;

import java.util.List;

public record FloodRasterSample(
        double lat,
        double lon,
        FloodHazardClass hazardClass,
        boolean cacheAvailable,
        String classificationStatus,
        String error,
        int pixelX,
        int pixelY,
        List<String> observedHazardClasses,
        String aggregationMethod,
        String rasterId,
        String rasterFile
) {
    public static FloodRasterSample cacheMiss(GeoPoint point) {
        return new FloodRasterSample(point.lat(), point.lon(), FloodHazardClass.UNKNOWN,
                false, "CACHE_MISS", "No local Bhuvan flood raster covers this point.",
                -1, -1, List.of(), "NONE", null, null);
    }
}
