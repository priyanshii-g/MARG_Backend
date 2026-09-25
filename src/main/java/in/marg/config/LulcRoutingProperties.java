package in.marg.config;

import in.marg.routing.LulcClass;
import in.marg.routing.LulcCostPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "marg.routing.lulc")
public class LulcRoutingProperties {
    private Map<String, Double> penalties = new LinkedHashMap<>();

    public Map<String, Double> getPenalties() {
        return penalties;
    }

    public void setPenalties(Map<String, Double> penalties) {
        this.penalties = penalties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(penalties);
    }

    public LulcCostPolicy toPolicy() {
        EnumMap<LulcClass, Double> values = new EnumMap<>(LulcClass.class);
        for (Map.Entry<String, Double> entry : penalties.entrySet()) {
            final LulcClass type;
            try {
                type = LulcClass.valueOf(entry.getKey().trim().toUpperCase());
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Unknown MARG LULC class in configuration: " + entry.getKey(), ex);
            }
            double value = entry.getValue() == null ? 0.0 : entry.getValue();
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("LULC penalty must be finite and >= 0 for " + entry.getKey());
            }
            values.put(type, value);
        }
        return new LulcCostPolicy(values);
    }
}
