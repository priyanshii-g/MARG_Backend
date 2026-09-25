package in.marg.routing;

import in.marg.flood.FloodHazardClass;

import java.util.EnumMap;
import java.util.Map;

/** Transparent, configurable preliminary flood-hazard routing penalties. */
public class FloodCostPolicy {
    private final Map<FloodHazardClass, Double> penalties;

    public FloodCostPolicy(Map<FloodHazardClass, Double> penalties) {
        this.penalties = new EnumMap<>(penalties);
    }

    public double penalty(FloodHazardClass hazardClass) {
        return penalties.getOrDefault(hazardClass, 0.0);
    }

    public Map<FloodHazardClass, Double> penalties() {
        return Map.copyOf(penalties);
    }
}
