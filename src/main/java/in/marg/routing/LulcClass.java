package in.marg.routing;

/** Coarse planning groups derived from Bhuvan's LULC feature description. */
public enum LulcClass {
    BUILT_UP,
    AGRICULTURE,
    FOREST,
    GRASS_GRAZING,
    BARREN_WASTELAND,
    WETLAND_WATER,
    SNOW,
    UNKNOWN;

    public static LulcClass fromFeatureInfo(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String s = raw.toLowerCase()
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");

        // Order matters: forestry plantations should not be mistaken for generic plantation/agriculture.
        if (containsAny(s, "evergreen", "semi evergreen", "deciduous", "scrub forest", "forest plantation", "tree clad", "forest")) {
            return FOREST;
        }
        if (containsAny(s, "water body", "water bodies", "river", "stream", "canal", "lake", "pond", "reservoir", "wetland", "mangrove", "swamp", "waterlogged")) {
            return WETLAND_WATER;
        }
        if (containsAny(s, "built up", "builtup", "urban", "industrial area", "rural settlement", "settlement", "mining", "quarry")) {
            return BUILT_UP;
        }
        if (containsAny(s, "crop land", "cropland", "kharif", "rabi", "zaid", "fallow", "agriculture", "shifting cultivation", "plantation", "orchard")) {
            return AGRICULTURE;
        }
        if (containsAny(s, "grassland", "grass grazing", "grazing")) {
            return GRASS_GRAZING;
        }
        if (containsAny(s, "barren", "wasteland", "waste land", "sandy area", "barren rocky", "scrub land", "ravinous", "gullied")) {
            return BARREN_WASTELAND;
        }
        if (containsAny(s, "snow", "glacial")) {
            return SNOW;
        }
        return UNKNOWN;
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }
}
