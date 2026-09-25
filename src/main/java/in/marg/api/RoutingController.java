package in.marg.api;

import in.marg.config.LulcRoutingProperties;
import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import in.marg.provider.LulcProvider;
import in.marg.provider.LulcSample;
import in.marg.routing.LulcCostLayer;
import in.marg.routing.CompositeCostSurface;
import in.marg.routing.CostLayer;
import in.marg.routing.CostSurfaceGeoJsonService;
import in.marg.routing.DistanceOnlyCostSurface;
import in.marg.routing.GreenfieldAStarRouter;
import in.marg.routing.LulcCostPolicy;
import in.marg.routing.RouteConstraints;
import in.marg.routing.RouteLulcMetricsService;
import in.marg.spatial.CoordinateProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RoutingController {
    private final StudyRegion region;
    private final GreenfieldAStarRouter router;
    private final LulcProvider lulcProvider;
    private final CoordinateProjection projection;
    private final double defaultCorridorBufferDeg;
    private final int defaultMaxRemoteSamples;
    private final double defaultResolutionMeters;
    private final RouteLulcMetricsService routeLulcMetrics;
    private final LulcRoutingProperties lulcProperties;
    private final CostSurfaceGeoJsonService costSurfaceGeoJsonService;

    public RoutingController(
            StudyRegion region,
            GreenfieldAStarRouter router,
            LulcProvider lulcProvider,
            CoordinateProjection projection,
            @Value("${marg.routing.corridor-buffer-deg:0.15}") double defaultCorridorBufferDeg,
            @Value("${marg.routing.resolution-meters:250}") double defaultResolutionMeters,
            @Value("${marg.bhuvan.lulc.max-remote-samples:4000}") int defaultMaxRemoteSamples,
            RouteLulcMetricsService routeLulcMetrics,
            LulcRoutingProperties lulcProperties,
            CostSurfaceGeoJsonService costSurfaceGeoJsonService) {
        this.region = region;
        this.router = router;
        this.lulcProvider = lulcProvider;
        this.projection = projection;
        this.defaultCorridorBufferDeg = defaultCorridorBufferDeg;
        this.defaultResolutionMeters = defaultResolutionMeters;
        this.defaultMaxRemoteSamples = defaultMaxRemoteSamples;
        this.routeLulcMetrics = routeLulcMetrics;
        this.lulcProperties = lulcProperties;
        this.costSurfaceGeoJsonService = costSurfaceGeoJsonService;
    }

    @GetMapping
    public Map<String, Object> routes(
            @RequestParam(defaultValue = "distance") String mode,
            @RequestParam(required = false) Double startLat,
            @RequestParam(required = false) Double startLon,
            @RequestParam(required = false) Double endLat,
            @RequestParam(required = false) Double endLon,
            @RequestParam(required = false) Double resolutionMeters,
            @RequestParam(required = false) Double corridorBufferDeg,
            @RequestParam(required = false) Integer maxRemoteSamples,
            @RequestParam(required = false) Double maxDeviationPercent
    ) {
        GeoPoint start = startLat == null ? region.origin() : new GeoPoint(startLat, require("startLon", startLon));
        GeoPoint goal = endLat == null ? region.destination() : new GeoPoint(endLat, require("endLon", endLon));
        double routingResolutionMeters = resolutionMeters == null ? defaultResolutionMeters : resolutionMeters;
        validate(region, start, goal, routingResolutionMeters);

        StudyRegion searchWindow = corridorWindow(region, start, goal,
                corridorBufferDeg == null ? defaultCorridorBufferDeg : corridorBufferDeg);

        String normalizedMode = mode.toLowerCase();
        if (!List.of("distance", "lulc", "compare").contains(normalizedMode)) {
            throw new IllegalArgumentException("mode must be distance, lulc, or compare");
        }
        if (maxDeviationPercent != null && (maxDeviationPercent < 0.0 || maxDeviationPercent > 200.0)) {
            throw new IllegalArgumentException("maxDeviationPercent must be between 0 and 200");
        }

        LulcCostPolicy policy = lulcProperties.toPolicy();
        List<Map<String, Object>> features = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("routingResolutionMeters", routingResolutionMeters);
        metadata.put("coordinateReferenceSystem", projection.crs());
        metadata.put("routingDistanceUnit", "metres internally; kilometres in route metrics");
        metadata.put("searchWindow", searchWindow);
        metadata.put("greenfield", true);
        metadata.put("existingRoadNetworkUsedForRouting", false);
        metadata.put("maxDeviationPercentRequested", maxDeviationPercent);
        if (maxDeviationPercent != null) {
            metadata.put("constraintMethod", "unconstrained-LULC-if-feasible; otherwise-Lagrangian-relaxation");
            metadata.put("constraintOptimizationApproximate", true);
        }

        if (normalizedMode.equals("lulc") || normalizedMode.equals("compare")) {
            int maxSamples = maxRemoteSamples == null ? defaultMaxRemoteSamples : maxRemoteSamples;
            List<LulcSample> preloaded = lulcProvider.preload(searchWindow, maxSamples);
            long successes = preloaded.stream().filter(LulcSample::remoteSuccess).count();
            long unknown = preloaded.stream().filter(s -> s.type() == in.marg.routing.LulcClass.UNKNOWN).count();
            metadata.put("bhuvanLulc", Map.of(
                    "dataset", lulcProvider.datasetId(),
                    "sampleResolutionDeg", lulcProvider.sampleResolution(),
                    "samplesRequested", preloaded.size(),
                    "remoteSuccesses", successes,
                    "unknownClassifications", unknown
            ));
        }

        List<GeoPoint> baseline = null;
        double baselineLengthKm = Double.NaN;
        if (normalizedMode.equals("distance") || normalizedMode.equals("compare") || maxDeviationPercent != null) {
            baseline = router.route(region, start, goal,
                    new DistanceOnlyCostSurface(), routingResolutionMeters, searchWindow);
            baselineLengthKm = router.routeLengthKm(baseline);
        }

        if (normalizedMode.equals("distance") || normalizedMode.equals("compare")) {
            features.add(feature("DISTANCE_BASELINE", baseline,
                    "geometric shortest greenfield baseline", false, baselineLengthKm, null));
        }

        if (normalizedMode.equals("lulc") || normalizedMode.equals("compare")) {
            List<CostLayer> layers = List.of(new LulcCostLayer(lulcProvider, policy));
            RouteConstraints constraints = RouteConstraints.none();
            if (maxDeviationPercent != null) {
                double maxRouteLengthKm = baselineLengthKm * (1.0 + maxDeviationPercent / 100.0);
                constraints = RouteConstraints.maxLengthKm(maxRouteLengthKm);
                metadata.put("maxAllowedRouteLengthKm", round(maxRouteLengthKm));
            }
            List<GeoPoint> points = router.route(region, start, goal,
                    new CompositeCostSurface(layers), routingResolutionMeters, searchWindow, constraints);
            Map<String, Object> metrics = routeLulcMetrics.summarize(points);
            metadata.put("lulcRouteMetrics", metrics);
            features.add(feature("LULC_AWARE", points,
                    "A* with provider-supplied Bhuvan LULC penalties", true, baselineLengthKm, maxDeviationPercent));
        }

        if (Double.isFinite(baselineLengthKm)) {
            metadata.put("distanceBaselineLengthKm", round(baselineLengthKm));
        }
        metadata.put("lulcPolicy", policy.penalties());

        return Map.of(
                "type", "FeatureCollection",
                "studyRegion", region.name(),
                "start", start,
                "goal", goal,
                "mode", normalizedMode,
                "metadata", metadata,
                "warning", "These are preliminary planning corridors. LULC is an informational planning layer; it is not a legal land-acquisition or clearance determination.",
                "features", features
        );
    }

    @GetMapping("/cost-surface")
    public Map<String, Object> lulcCostSurface(
            @RequestParam(defaultValue = "0.15") double corridorBufferDeg,
            @RequestParam(required = false) Integer maxRemoteSamples) {
        GeoPoint start = region.origin();
        GeoPoint goal = region.destination();
        StudyRegion searchWindow = corridorWindow(region, start, goal, corridorBufferDeg);
        int maxSamples = maxRemoteSamples == null ? defaultMaxRemoteSamples : maxRemoteSamples;
        return costSurfaceGeoJsonService.lulcSurface(searchWindow, maxSamples);
    }

    private Map<String, Object> feature(String profile, List<GeoPoint> points, String explanation,
                                         boolean dataDriven, double baselineLengthKm,
                                         Double maxDeviationPercent) {
        List<List<Double>> coordinates = points.stream().map(p -> List.of(p.lon(), p.lat())).toList();
        double km = router.routeLengthKm(points);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("profile", profile);
        properties.put("routeLengthKm", round(km));
        properties.put("pointCount", points.size());
        properties.put("greenfield", true);
        properties.put("dataDriven", dataDriven);
        properties.put("explanation", explanation);
        if (Double.isFinite(baselineLengthKm) && baselineLengthKm > 0.0) {
            double deviation = ((km / baselineLengthKm) - 1.0) * 100.0;
            properties.put("distanceBaselineKm", round(baselineLengthKm));
            properties.put("deviationPercent", round(deviation));
            if (maxDeviationPercent != null) {
                properties.put("maxDeviationPercent", maxDeviationPercent);
                properties.put("withinMaxDeviation", deviation <= maxDeviationPercent + 1e-8);
            }
        }
        Map<String, Object> geometry = Map.of("type", "LineString", "coordinates", coordinates);
        return Map.of("type", "Feature", "properties", properties, "geometry", geometry);
    }

    private static StudyRegion corridorWindow(StudyRegion full, GeoPoint a, GeoPoint b, double bufferDeg) {
        if (bufferDeg < 0.02 || bufferDeg > 0.30) {
            throw new IllegalArgumentException("corridorBufferDeg must be between 0.02 and 0.30");
        }
        double minLat = Math.max(full.minLat(), Math.min(a.lat(), b.lat()) - bufferDeg);
        double maxLat = Math.min(full.maxLat(), Math.max(a.lat(), b.lat()) + bufferDeg);
        double minLon = Math.max(full.minLon(), Math.min(a.lon(), b.lon()) - bufferDeg);
        double maxLon = Math.min(full.maxLon(), Math.max(a.lon(), b.lon()) + bufferDeg);
        return new StudyRegion(full.name() + " / A-B corridor", minLat, minLon, maxLat, maxLon, a, b);
    }

    private static double require(String name, Double value) {
        if (value == null) throw new IllegalArgumentException(name + " is required when the paired coordinate is provided");
        return value;
    }

    private static void validate(StudyRegion region, GeoPoint start, GeoPoint goal, double resolutionMeters) {
        if (!region.contains(start) || !region.contains(goal)) {
            throw new IllegalArgumentException("Both endpoints must be inside the configured study region");
        }
        if (resolutionMeters < 50.0 || resolutionMeters > 5000.0 || !Double.isFinite(resolutionMeters)) {
            throw new IllegalArgumentException("resolutionMeters must be between 50 and 5000");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
