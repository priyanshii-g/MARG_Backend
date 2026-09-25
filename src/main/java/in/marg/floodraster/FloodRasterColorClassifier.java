package in.marg.floodraster;

import in.marg.flood.FloodHazardClass;

import java.awt.Color;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classifies pixels against the published Bhuvan Flood Hazard WMS legend.
 * The RGB values are taken from the legend graphic supplied for this MARG
 * integration. Classification uses nearest-colour distance with a tolerance
 * so anti-aliased/encoded pixels do not become false UNKNOWN values.
 */
public final class FloodRasterColorClassifier {
    public record LegendColor(FloodHazardClass hazardClass, int r, int g, int b) {}

    private static final Map<FloodHazardClass, LegendColor> LEGEND = new LinkedHashMap<>();
    static {
        LEGEND.put(FloodHazardClass.VERY_LOW, new LegendColor(FloodHazardClass.VERY_LOW, 255, 235, 175));
        LEGEND.put(FloodHazardClass.LOW, new LegendColor(FloodHazardClass.LOW, 254, 211, 127));
        LEGEND.put(FloodHazardClass.MODERATE, new LegendColor(FloodHazardClass.MODERATE, 255, 190, 140));
        LEGEND.put(FloodHazardClass.HIGH, new LegendColor(FloodHazardClass.HIGH, 255, 170, 0));
        LEGEND.put(FloodHazardClass.VERY_HIGH, new LegendColor(FloodHazardClass.VERY_HIGH, 245, 122, 182));
    }

    private FloodRasterColorClassifier() {}

    public static Map<FloodHazardClass, LegendColor> legend() {
        return Map.copyOf(LEGEND);
    }

    public static FloodHazardClass classify(int argb, int tolerance) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 10) return FloodHazardClass.NOT_IN_HAZARD_LAYER;
        int r = (argb >>> 16) & 0xff;
        int g = (argb >>> 8) & 0xff;
        int b = argb & 0xff;
        return classifyRgb(r, g, b, tolerance);
    }

    public static FloodHazardClass classifyRgb(int r, int g, int b, int tolerance) {
        if (r > 248 && g > 248 && b > 248) return FloodHazardClass.NOT_IN_HAZARD_LAYER;

        return LEGEND.values().stream()
                .map(c -> new Distance(c.hazardClass(), squaredDistance(r, g, b, c.r(), c.g(), c.b())))
                .min(Comparator.comparingLong(Distance::distance))
                .filter(d -> d.distance() <= (long) tolerance * tolerance)
                .map(Distance::hazardClass)
                .orElse(FloodHazardClass.UNKNOWN);
    }

    public static String hex(FloodHazardClass hazardClass) {
        LegendColor c = LEGEND.get(hazardClass);
        if (c == null) return null;
        return "#%02x%02x%02x".formatted(c.r(), c.g(), c.b());
    }

    private static long squaredDistance(int r, int g, int b, int r2, int g2, int b2) {
        long dr = r - r2;
        long dg = g - g2;
        long db = b - b2;
        return dr * dr + dg * dg + db * db;
    }

    private record Distance(FloodHazardClass hazardClass, long distance) {}
}
