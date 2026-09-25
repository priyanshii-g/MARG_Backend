package in.marg.api;

import in.marg.config.FloodRoutingProperties;
import in.marg.config.LulcRoutingProperties;
import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import in.marg.routing.CompositeCostSurfaceFactory;
import in.marg.routing.DistanceOnlyCostSurface;
import in.marg.routing.GreenfieldAStarRouter;
import in.marg.routing.RouteConstraints;
import in.marg.spatial.CoordinateProjection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import in.marg.floodraster.BhuvanFloodRasterCache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes/composite")
public class CompositeRoutingController {

    private static final double ROUTING_RESOLUTION_METERS = 250.0;

    private final StudyRegion region;
    private final GreenfieldAStarRouter router;
    private final CoordinateProjection projection;
    private final CompositeCostSurfaceFactory factory;

    private final LulcRoutingProperties lulcProperties;
    private final FloodRoutingProperties floodProperties;

    private final double defaultCorridorBufferDeg;
    private final int defaultMaxLulcSamples;
    private final BhuvanFloodRasterCache floodRasterCache;

    public CompositeRoutingController(
            StudyRegion region,
            GreenfieldAStarRouter router,
            CoordinateProjection projection,
            CompositeCostSurfaceFactory factory,
            LulcRoutingProperties lulcProperties,
            BhuvanFloodRasterCache floodRasterCache,
            FloodRoutingProperties floodProperties,
            @Value("${marg.routing.corridor-buffer-deg:0.15}")
            double defaultCorridorBufferDeg,
            @Value("${marg.bhuvan.lulc.max-remote-samples:4000}")
            int defaultMaxLulcSamples) {

        this.region = region;
        this.router = router;
        this.projection = projection;
        this.factory = factory;
        this.lulcProperties = lulcProperties;
        this.floodProperties = floodProperties;
        this.defaultCorridorBufferDeg =
                defaultCorridorBufferDeg;
        this.defaultMaxLulcSamples =
                defaultMaxLulcSamples;
        this.floodRasterCache = floodRasterCache;
    }

    @GetMapping
    public Map<String, Object> route(
            @RequestParam(required = false) Double startLat,
            @RequestParam(required = false) Double startLon,
            @RequestParam(required = false) Double endLat,
            @RequestParam(required = false) Double endLon,
            @RequestParam(required = false) Double resolutionMeters,
            @RequestParam(required = false) Double corridorBufferDeg,
            @RequestParam(required = false) Integer maxLulcSamples,
            @RequestParam(required = false) Double maxDeviationPercent) {

        GeoPoint start =
                startLat == null
                        ? region.origin()
                        : new GeoPoint(
                                startLat,
                                require(
                                        "startLon",
                                        startLon));

        GeoPoint goal =
                endLat == null
                        ? region.destination()
                        : new GeoPoint(
                                endLat,
                                require(
                                        "endLon",
                                        endLon));

        double resolution =
                resolutionMeters == null
                        ? ROUTING_RESOLUTION_METERS
                        : resolutionMeters;

        if (Math.abs(
                resolution
                        - ROUTING_RESOLUTION_METERS)
                > 1e-9) {

            throw new IllegalArgumentException(
                    "Composite routing is fixed at "
                            + "the canonical 250 m grid.");
        }

        validateEndpoints(
                start,
                goal);

        double buffer =
                corridorBufferDeg == null
                        ? defaultCorridorBufferDeg
                        : corridorBufferDeg;

        StudyRegion searchWindow =
                corridorWindow(
                        region,
                        start,
                        goal,
                        buffer);

        BhuvanFloodRasterCache.PreloadResult floodPreload =
        floodRasterCache.preload(
                searchWindow.minLat(),
                searchWindow.minLon(),
                searchWindow.maxLat(),
                searchWindow.maxLon(),
                null,
                null
        );

        int maxSamples =
                maxLulcSamples == null
                        ? defaultMaxLulcSamples
                        : maxLulcSamples;

        if (maxSamples < 1
                || maxSamples > 10000) {

            throw new IllegalArgumentException(
                    "maxLulcSamples must be "
                            + "between 1 and 10000");
        }

        CompositeCostSurfaceFactory.BuildResult built =
                factory.build(
                        searchWindow,
                        maxSamples);

        // Baseline is computed with exactly the
        // same search window and 250 m grid.
        List<GeoPoint> baseline =
                router.route(
                        region,
                        start,
                        goal,
                        new DistanceOnlyCostSurface(),
                        ROUTING_RESOLUTION_METERS,
                        searchWindow);

        double baselineLengthKm =
                router.routeLengthKm(
                        baseline);

        RouteConstraints constraints =
                RouteConstraints.none();

        if (maxDeviationPercent != null) {

            if (!Double.isFinite(
                    maxDeviationPercent)
                    || maxDeviationPercent < 0.0
                    || maxDeviationPercent > 200.0) {

                throw new IllegalArgumentException(
                        "maxDeviationPercent must "
                                + "be between 0 and 200");
            }

            double maxLengthKm =
                    baselineLengthKm
                            * (1.0
                            + maxDeviationPercent
                            / 100.0);

            constraints =
                    RouteConstraints.maxLengthKm(
                            maxLengthKm);
        }

        List<GeoPoint> route =
                router.route(
                        region,
                        start,
                        goal,
                        built.surface(),
                        ROUTING_RESOLUTION_METERS,
                        searchWindow,
                        constraints);

        double routeLengthKm =
                router.routeLengthKm(route);

        double deviationPercent =
                baselineLengthKm > 0.0
                        ? ((routeLengthKm
                        / baselineLengthKm)
                        - 1.0) * 100.0
                        : Double.NaN;

        var flood =
                built.floodPreload().mosaic();

        Map<String, Object> metadata =
                new LinkedHashMap<>();

        metadata.put(
                "routingResolutionMeters",
                ROUTING_RESOLUTION_METERS);

        metadata.put(
                "coordinateReferenceSystem",
                projection.crs());

        metadata.put(
                "routingDistanceUnit",
                "metres internally; "
                        + "kilometres in metrics");

        metadata.put(
                "searchWindow",
                searchWindow);

        metadata.put(
                "greenfield",
                true);

        metadata.put(
                "layers",
                List.of(
                        "LULC",
                        "FLOOD_HAZARD",
                        "SLOPE",
                        "PROTECTED_AREA"));

        metadata.put(
                "compositeCost",
                "stepKm * (1 + LULC_penalty "
                        + "+ Flood_penalty "
                        + "+ Slope_penalty)");

        metadata.put(
                "protectedAreaRule",
                "PROTECTED_AREA and "
                        + "PROTECTED_AREA_NODATA "
                        + "are hard constraints");

        metadata.put(
                "sourceResolutionWarning",
                "250 m is the routing-grid spacing. "
                        + "It does not change the native "
                        + "resolution/provenance of source datasets.");

        metadata.put(
                "penaltiesArePreliminary",
                true);

        metadata.put(
                "normalizationWarning",
                "Current preliminary layer penalties "
                        + "are summed in their existing scales. "
                        + "User weights and normalization are "
                        + "intentionally not enabled yet.");

        metadata.put(
                "lulc",
                Map.of(
                        "samplesLoaded",
                        built.lulcSamplesLoaded(),
                        "penalties",
                        lulcProperties
                                .toPolicy()
                                .penalties()));

        metadata.put(
                "floodRaster",
                Map.of(
                        "rasterId",
                        flood.id(),
                        "source",
                        flood.sourceUrl(),
                        "layer",
                        flood.layerName(),
                        "sourceResolution",
                        flood.sourceResolution(),
                        "width",
                        flood.width(),
                        "height",
                        flood.height(),
                        "remoteRequestsDuringPreload",
                        built.floodPreload()
                                .remoteRequests(),
                        "reusedExistingCache",
                        built.floodPreload()
                                .reused()));

        metadata.put(
                "floodPenalties",
                floodProperties
                        .toPolicy()
                        .penalties());

        Map<String, Object> slopePolicy =
            new LinkedHashMap<>();

        slopePolicy.put("freeMaxDegrees", 5.0);
        slopePolicy.put("lowMaxDegrees", 10.0);
        slopePolicy.put("moderateMaxDegrees", 15.0);
        slopePolicy.put("steepMaxDegrees", 25.0);
        slopePolicy.put("verySteepMaxDegrees", 30.0);

        slopePolicy.put("lowPenalty", 0.15);
        slopePolicy.put("moderatePenalty", 0.40);
        slopePolicy.put("steepPenalty", 1.00);
        slopePolicy.put("verySteepPenalty", 2.00);

        slopePolicy.put("blockNoData", true);
        slopePolicy.put("blockAboveVerySteep", true);

        metadata.put("slopePolicy", slopePolicy);

        metadata.put(
                "distanceBaselineLengthKm",
                round(baselineLengthKm));

        metadata.put(
                "routeLengthKm",
                round(routeLengthKm));

        metadata.put(
                "deviationPercent",
                round(deviationPercent));

        metadata.put(
                "constraintMethod",
                maxDeviationPercent == null
                        ? "none"
                        : "unconstrained-A*-if-feasible-"
                        + "otherwise-Lagrangian-relaxation");

        metadata.put(
                "constraintOptimizationApproximate",
                maxDeviationPercent != null);

        List<List<Double>> coordinates =
                route.stream()
                        .map(point ->
                                List.of(
                                        point.lon(),
                                        point.lat()))
                        .toList();

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "profile",
                "COMPOSITE_4_LAYER");

        properties.put(
                "routeLengthKm",
                round(routeLengthKm));

        properties.put(
                "distanceBaselineKm",
                round(baselineLengthKm));

        properties.put(
                "deviationPercent",
                round(deviationPercent));

        properties.put(
                "pointCount",
                route.size());

        properties.put(
                "greenfield",
                true);

        properties.put(
                "dataDriven",
                true);

        properties.put(
                "explanation",
                "A* over the common projected "
                        + "250 m routing grid using "
                        + "LULC + flood + slope + "
                        + "protected-area layers.");

        Map<String, Object> feature =
                Map.of(
                        "type",
                        "Feature",
                        "properties",
                        properties,
                        "geometry",
                        Map.of(
                                "type",
                                "LineString",
                                "coordinates",
                                coordinates));

        return Map.of(
                "type",
                "FeatureCollection",
                "studyRegion",
                region.name(),
                "start",
                start,
                "goal",
                goal,
                "mode",
                "COMPOSITE_4_LAYER",
                "metadata",
                metadata,
                "warning",
                "Preliminary greenfield planning "
                        + "corridor only; not a legal "
                        + "land-acquisition or clearance "
                        + "determination.",
                "features",
                List.of(feature));
    }

    private void validateEndpoints(
            GeoPoint start,
            GeoPoint goal) {

        if (!region.contains(start)
                || !region.contains(goal)) {

            throw new IllegalArgumentException(
                    "Both endpoints must be inside "
                            + "the configured study region");
        }
    }

    private static StudyRegion corridorWindow(
            StudyRegion full,
            GeoPoint a,
            GeoPoint b,
            double bufferDeg) {

        if (bufferDeg < 0.02
                || bufferDeg > 0.30) {

            throw new IllegalArgumentException(
                    "corridorBufferDeg must be "
                            + "between 0.02 and 0.30");
        }

        double minLat =
                Math.max(
                        full.minLat(),
                        Math.min(
                                a.lat(),
                                b.lat())
                                - bufferDeg);

        double maxLat =
                Math.min(
                        full.maxLat(),
                        Math.max(
                                a.lat(),
                                b.lat())
                                + bufferDeg);

        double minLon =
                Math.max(
                        full.minLon(),
                        Math.min(
                                a.lon(),
                                b.lon())
                                - bufferDeg);

        double maxLon =
                Math.min(
                        full.maxLon(),
                        Math.max(
                                a.lon(),
                                b.lon())
                                + bufferDeg);

        return new StudyRegion(
                full.name()
                        + " / composite A-B corridor",
                minLat,
                minLon,
                maxLat,
                maxLon,
                a,
                b);
    }

    private static double require(
            String name,
            Double value) {

        if (value == null) {
            throw new IllegalArgumentException(
                    name
                            + " is required when the "
                            + "paired coordinate is provided");
        }

        return value;
    }

    private static double round(
            double value) {

        return Math.round(
                value * 100.0)
                / 100.0;
    }
}