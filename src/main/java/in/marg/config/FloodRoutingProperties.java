package in.marg.config;

import in.marg.flood.FloodHazardClass;
import in.marg.routing.FloodCostPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configurable MARG routing penalties for the cached flood-hazard layer. */
@ConfigurationProperties(prefix = "marg.routing.flood")
public class FloodRoutingProperties {
    private Map<String, Double> penalties = new LinkedHashMap<>();

    public Map<String, Double> getPenalties() {
        return penalties;
    }

    public void setPenalties(Map<String, Double> penalties) {
        this.penalties = penalties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(penalties);
    }

    public FloodCostPolicy toPolicy() {
        Map<FloodHazardClass, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : penalties.entrySet()) {
            final FloodHazardClass type;
            try {
                type = FloodHazardClass.valueOf(entry.getKey().trim().toUpperCase());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Unknown flood hazard class in configuration: " + entry.getKey(), ex);
            }
            double value = entry.getValue() == null ? 0.0 : entry.getValue();
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Flood penalty must be finite and >= 0 for " + entry.getKey());
            }
            values.put(type, value);
        }
        return new FloodCostPolicy(values);
    }
}
