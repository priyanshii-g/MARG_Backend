package in.marg.routing;

import in.marg.config.LulcRoutingProperties;
import in.marg.model.StudyRegion;
import in.marg.provider.LulcProvider;
import in.marg.provider.LulcSample;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a diagnostic GeoJSON grid so the planner can inspect the LULC cost surface. */
@Service
public class CostSurfaceGeoJsonService {
    private final LulcProvider provider;
    private final LulcCostPolicy policy;

    public CostSurfaceGeoJsonService(LulcProvider provider, LulcRoutingProperties properties) {
        this.provider = provider;
        this.policy = properties.toPolicy();
    }

    public Map<String, Object> lulcSurface(StudyRegion region, int maxSamples) {
        List<LulcSample> samples = provider.preload(region, maxSamples);
        double half = provider.sampleResolution() / 2.0;
        List<Map<String, Object>> features = new ArrayList<>(samples.size());
        for (LulcSample sample : samples) {
            double minLat = Math.max(region.minLat(), sample.lat() - half);
            double maxLat = Math.min(region.maxLat(), sample.lat() + half);
            double minLon = Math.max(region.minLon(), sample.lon() - half);
            double maxLon = Math.min(region.maxLon(), sample.lon() + half);
            List<List<Double>> ring = List.of(
                    List.of(minLon, minLat),
                    List.of(maxLon, minLat),
                    List.of(maxLon, maxLat),
                    List.of(minLon, maxLat),
                    List.of(minLon, minLat)
            );
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("lulcClass", sample.type().name());
            props.put("penaltyMultiplier", policy.penalty(sample.type()));
            props.put("remoteSuccess", sample.remoteSuccess());
            props.put("sampleLat", sample.lat());
            props.put("sampleLon", sample.lon());
            features.add(Map.of(
                    "type", "Feature",
                    "properties", props,
                    "geometry", Map.of("type", "Polygon", "coordinates", List.of(ring))
            ));
        }
        return Map.of(
                "type", "FeatureCollection",
                "layer", "bhuvan-lulc-cost-surface",
                "dataset", provider.datasetId(),
                "sampleResolutionDeg", provider.sampleResolution(),
                "studyRegion", region.name(),
                "features", features
        );
    }
}
