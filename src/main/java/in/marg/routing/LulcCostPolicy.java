package in.marg.routing;

import java.util.EnumMap;
import java.util.Map;

/** Transparent, configurable preliminary LULC penalties for the planning study. */
public class LulcCostPolicy {
    private final Map<LulcClass, Double> penalties;

    public LulcCostPolicy(Map<LulcClass, Double> penalties) {
        this.penalties = new EnumMap<>(penalties);
    }

    public double penalty(LulcClass type) {
        return penalties.getOrDefault(type, 0.0);
    }

    public Map<LulcClass, Double> penalties() {
        return Map.copyOf(penalties);
    }
}
