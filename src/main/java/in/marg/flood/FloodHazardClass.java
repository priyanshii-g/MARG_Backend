package in.marg.flood;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Coarse historical flood-hazard categories documented by NRSC for Assam.
 * The numeric weight is the hazard-category weight published in the Assam
 * flood-hazard zonation schema; MARG does not yet use it in A* routing.
 */
public enum FloodHazardClass {
    VERY_HIGH(5, "16-18 inundation events in the Assam schema"),
    HIGH(4, "13-15 inundation events in the Assam schema"),
    MODERATE(3, "9-12 inundation events in the Assam schema"),
    LOW(2, "5-8 inundation events in the Assam schema"),
    VERY_LOW(1, "1-4 inundation events in the Assam schema"),
    NOT_IN_HAZARD_LAYER(0, "No hazard feature returned at the queried point"),
    UNKNOWN(-1, "A response was received but the hazard class could not be parsed");

    private final int publishedCategoryWeight;
    private final String description;

    FloodHazardClass(int publishedCategoryWeight, String description) {
        this.publishedCategoryWeight = publishedCategoryWeight;
        this.description = description;
    }

    public int publishedCategoryWeight() {
        return publishedCategoryWeight;
    }

    public String description() {
        return description;
    }

    public static FloodHazardClass fromFeatureInfo(String raw) {
        if (raw == null || raw.isBlank()) {
            return NOT_IN_HAZARD_LAYER;
        }
        return fromText(raw);
    }

    /** Parse a set of WFS properties while preferring hazard-related fields. */
    public static ParsedProperties parseProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return new ParsedProperties(NOT_IN_HAZARD_LAYER, null, null);
        }

        // Prefer fields whose names clearly refer to hazard classification.
        for (Map.Entry<String, String> e : properties.entrySet()) {
            String key = normalize(e.getKey());
            if (key.contains("hazard") || key.contains("severity") || key.contains("zone") || key.equals("hz")) {
                FloodHazardClass c = fromText(e.getValue());
                if (c != UNKNOWN) {
                    return new ParsedProperties(c, e.getKey(), e.getValue());
                }
            }
        }

        // The live Bhuvan AS_HZ WMS exposes GRID_CODE. NRSC documentation
        // defines the hazard-zone factor as 1=Very Low, 2=Low, 3=Moderate,
        // 4=High, 5=Very High. The GetCapabilities response does not expose
        // a field dictionary, so this is an explicit, documented mapping
        // convention rather than a claim that the live service metadata
        // itself spells out the GRID_CODE definition.
        for (Map.Entry<String, String> e : properties.entrySet()) {
            if (normalize(e.getKey()).equals("grid code") && e.getValue() != null && !e.getValue().isBlank()) {
                FloodHazardClass c = fromGridCode(e.getValue());
                if (c != UNKNOWN) {
                    return new ParsedProperties(c, e.getKey(), e.getValue());
                }
                return new ParsedProperties(UNKNOWN, e.getKey(), e.getValue());
            }
        }

        // Then inspect values, but only accept explicit category labels/ranges.
        // Do not match arbitrary substrings such as a property value containing
        // the word "high" unless it clearly denotes the hazard category.
        for (Map.Entry<String, String> e : properties.entrySet()) {
            FloodHazardClass c = fromExplicitCategoryText(e.getValue());
            if (c != UNKNOWN) {
                return new ParsedProperties(c, e.getKey(), e.getValue());
            }
        }

        return new ParsedProperties(UNKNOWN, null, null);
    }


    /**
     * Map the numeric flood-hazard zone factor used by NRSC:
     * 1=Very Low, 2=Low, 3=Moderate, 4=High, 5=Very High.
     */
    public static FloodHazardClass fromGridCode(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try {
            return switch (Integer.parseInt(raw.trim())) {
                case 1 -> VERY_LOW;
                case 2 -> LOW;
                case 3 -> MODERATE;
                case 4 -> HIGH;
                case 5 -> VERY_HIGH;
                default -> UNKNOWN;
            };
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    /**
     * Conservatively aggregate multiple recognized hazard classes by taking
     * the highest published category weight. This is used only when the WMS
     * query still exposes multiple distinct hazard polygons after footprint
     * refinement. It prevents a high/very-high class from being hidden by an
     * average and never treats the result as UNKNOWN merely because several
     * classes were returned.
     */
    public static FloodHazardClass conservativeMax(Collection<FloodHazardClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return UNKNOWN;
        }
        return classes.stream()
                .filter(c -> c != null && c != UNKNOWN && c != NOT_IN_HAZARD_LAYER)
                .max(Comparator.comparingInt(FloodHazardClass::publishedCategoryWeight))
                .orElse(UNKNOWN);
    }

    /**
     * Parse only an explicit flood-hazard category label or documented
     * inundation-frequency range. This is intentionally stricter than
     * fromText(...) so unrelated property text such as "Lowland road area"
     * cannot become a hazard classification merely because it contains a
     * category word.
     */
    private static FloodHazardClass fromExplicitCategoryText(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;

        String s = normalize(raw);
        return switch (s) {
            case "very high", "veryhigh", "16 18", "18 16" -> VERY_HIGH;
            case "high", "13 15", "15 13" -> HIGH;
            case "moderate", "9 12", "12 9" -> MODERATE;
            case "low", "5 8", "8 5" -> LOW;
            case "very low", "verylow", "1 4", "4 1" -> VERY_LOW;
            default -> UNKNOWN;
        };
    }

    private static FloodHazardClass fromText(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String s = normalize(raw);

        // Check longer labels first because "high" is contained in "very high".
        if (containsAny(s, "very high", "veryhigh", "16 18", "18 16")) return VERY_HIGH;
        if (containsAny(s, "high", "13 15", "15 13")) return HIGH;
        if (containsAny(s, "moderate", "9 12", "12 9")) return MODERATE;
        if (containsAny(s, "very low", "verylow", "1 4", "4 1")) return VERY_LOW;
        if (containsAny(s, "low", "5 8", "8 5")) return LOW;
        if (containsAny(s, "no feature", "no data", "nodata", "not flooded", "not in hazard", "normal")) {
            return NOT_IN_HAZARD_LAYER;
        }
        return UNKNOWN;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }

    public record ParsedProperties(FloodHazardClass hazardClass, String fieldName, String fieldValue) {}
}
