package in.marg.routing;

import in.marg.model.GeoPoint;
import in.marg.provider.LulcProvider;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RouteLulcMetricsService {
    private final GreenfieldAStarRouter router;
    private final LulcProvider provider;

    public RouteLulcMetricsService(GreenfieldAStarRouter router, LulcProvider provider) {
        this.router = router;
        this.provider = provider;
    }

    public Map<String, Object> summarize(List<GeoPoint> route) {
        EnumMap<LulcClass, Double> kmByClass = new EnumMap<>(LulcClass.class);
        double total = 0.0;
        for (int i = 1; i < route.size(); i++) {
            GeoPoint a = route.get(i - 1);
            GeoPoint b = route.get(i);
            GeoPoint mid = new GeoPoint((a.lat() + b.lat()) / 2.0, (a.lon() + b.lon()) / 2.0);
            double segmentKm = router.routeLengthKm(List.of(a, b));
            LulcClass type = provider.sample(mid).type();
            kmByClass.merge(type, segmentKm, Double::sum);
            total += segmentKm;
        }

        Map<String, Double> rounded = new java.util.LinkedHashMap<>();
        for (var entry : kmByClass.entrySet()) {
            rounded.put(entry.getKey().name(), Math.round(entry.getValue() * 100.0) / 100.0);
        }
        return Map.of(
                "routeLengthKm", Math.round(total * 100.0) / 100.0,
                "approxRouteExposureKmByLulc", rounded,
                "dataProvider", provider.getClass().getSimpleName(),
                "dataset", provider.datasetId()
        );
    }
}
