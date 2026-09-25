package in.marg.routing;

import in.marg.config.FloodRoutingProperties;
import in.marg.config.LulcRoutingProperties;
import in.marg.config.SlopeRoutingProperties;
import in.marg.environment.ProtectedAreaRasterProvider;
import in.marg.flood.FloodHazardClass;
import in.marg.floodraster.BhuvanFloodRasterCache;
import in.marg.floodraster.FloodRasterSample;
import in.marg.model.GeoPoint;
import in.marg.provider.LulcProvider;
import in.marg.provider.LulcSample;
import in.marg.spatial.CoordinateProjection;
import in.marg.spatial.MetricPoint;
import in.marg.terrain.SlopeRasterProvider;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompositeRouteMetricsService {

    /*
     * Diagnostic sampling spacing.
     *
     * The actual routing grid is 250 m. We sample diagnostics
     * every <=125 m along the returned geometry so that long
     * collinear segments do not hide intermediate raster classes.
     */
    private static final double DIAGNOSTIC_SAMPLE_SPACING_METERS = 125.0;

    private final CoordinateProjection projection;
    private final LulcProvider lulcProvider;
    private final LulcRoutingProperties lulcProperties;
    private final BhuvanFloodRasterCache floodRasterCache;
    private final FloodRoutingProperties floodProperties;
    private final SlopeRasterProvider slopeRasterProvider;
    private final SlopeRoutingProperties slopeProperties;
    private final ProtectedAreaRasterProvider protectedAreaRasterProvider;

    public CompositeRouteMetricsService(
            CoordinateProjection projection,
            LulcProvider lulcProvider,
            LulcRoutingProperties lulcProperties,
            BhuvanFloodRasterCache floodRasterCache,
            FloodRoutingProperties floodProperties,
            SlopeRasterProvider slopeRasterProvider,
            SlopeRoutingProperties slopeProperties,
            ProtectedAreaRasterProvider protectedAreaRasterProvider) {

        this.projection = projection;
        this.lulcProvider = lulcProvider;
        this.lulcProperties = lulcProperties;
        this.floodRasterCache = floodRasterCache;
        this.floodProperties = floodProperties;
        this.slopeRasterProvider = slopeRasterProvider;
        this.slopeProperties = slopeProperties;
        this.protectedAreaRasterProvider =
                protectedAreaRasterProvider;
    }

    public Map<String, Object> summarize(
            List<GeoPoint> route) {

        EnumMap<LulcClass, Double> lulcKm =
                new EnumMap<>(LulcClass.class);

        EnumMap<FloodHazardClass, Double> floodKm =
                new EnumMap<>(FloodHazardClass.class);

        LinkedHashMap<String, Double> slopeKm =
                new LinkedHashMap<>();

        double totalKm = 0.0;

        double lulcPenaltyKm = 0.0;
        double floodPenaltyKm = 0.0;
        double slopePenaltyKm = 0.0;
        double protectedAreaKm = 0.0;

        int diagnosticSamples = 0;

        int floodCacheMisses = 0;

        int protectedAreaSamples = 0;
        int protectedAreaNoDataSamples = 0;

        int slopeOverLimitSamples = 0;
        int slopeNoDataSamples = 0;

        for (int i = 1; i < route.size(); i++) {

            GeoPoint from = route.get(i - 1);
            GeoPoint to = route.get(i);

            MetricPoint a =
                    projection.forward(from);

            MetricPoint b =
                    projection.forward(to);

            double dx = b.x() - a.x();
            double dy = b.y() - a.y();

            double segmentMeters =
                    Math.hypot(dx, dy);

            if (segmentMeters <= 0.0) {
                continue;
            }

            int parts = Math.max(
                    1,
                    (int) Math.ceil(
                            segmentMeters
                                    / DIAGNOSTIC_SAMPLE_SPACING_METERS));

            double sampleLengthKm =
                    segmentMeters
                            / parts
                            / 1000.0;

            for (int j = 0; j < parts; j++) {

                /*
                 * Sample at the midpoint of each diagnostic
                 * sub-segment.
                 */
                double t =
                        (j + 0.5) / parts;

                MetricPoint sampleMetric =
                        new MetricPoint(
                                a.x() + t * dx,
                                a.y() + t * dy);

                GeoPoint point =
                        projection.inverse(sampleMetric);

                diagnosticSamples++;
                totalKm += sampleLengthKm;

                /*
                 * ---------------------------
                 * LULC
                 * ---------------------------
                 */
                LulcSample lulcSample =
                        lulcProvider.sample(point);

                LulcClass lulcClass =
                        lulcSample.type();

                lulcKm.merge(
                        lulcClass,
                        sampleLengthKm,
                        Double::sum);

                double lulcPenalty =
                        lulcProperties
                                .toPolicy()
                                .penalty(lulcClass);

                lulcPenaltyKm +=
                        sampleLengthKm
                                * lulcPenalty;

                /*
                 * ---------------------------
                 * FLOOD
                 * ---------------------------
                 */
                FloodRasterSample floodSample =
                        floodRasterCache.sample(point);

                if (!floodSample.cacheAvailable()) {

                    floodCacheMisses++;

                } else {

                    FloodHazardClass hazard =
                            floodSample.hazardClass();

                    floodKm.merge(
                            hazard,
                            sampleLengthKm,
                            Double::sum);

                    double floodPenalty =
                            floodProperties
                                    .toPolicy()
                                    .penalty(hazard);

                    floodPenaltyKm +=
                            sampleLengthKm
                                    * floodPenalty;
                }

                /*
                 * ---------------------------
                 * SLOPE
                 * ---------------------------
                 */
                Double slopeDegrees =
                        slopeRasterProvider.sampleOrNull(
                                point.lat(),
                                point.lon());

                String slopeBand =
                        classifySlope(slopeDegrees);

                slopeKm.merge(
                        slopeBand,
                        sampleLengthKm,
                        Double::sum);

                if (slopeDegrees == null) {

                    slopeNoDataSamples++;

                } else if (
                        slopeDegrees
                                > slopeProperties
                                .getVerySteepMaxDegrees()) {

                    slopeOverLimitSamples++;
                }

                slopePenaltyKm +=
                        sampleLengthKm
                                * slopePenalty(
                                        slopeDegrees);

                /*
                 * ---------------------------
                 * PROTECTED AREA
                 * ---------------------------
                 */
                Double protectedMask =
                        protectedAreaRasterProvider
                                .sampleOrNull(
                                        point.lat(),
                                        point.lon());

                if (protectedMask == null) {

                    protectedAreaNoDataSamples++;

                } else if (
                        Math.round(protectedMask)
                                == 1L) {

                    protectedAreaSamples++;
                    protectedAreaKm += sampleLengthKm;
                }
            }
        }

        /*
         * Convert enum maps to JSON-friendly ordered maps.
         */
        Map<String, Double> lulcExposure =
                new LinkedHashMap<>();

        for (Map.Entry<LulcClass, Double> entry
                : lulcKm.entrySet()) {

            lulcExposure.put(
                    entry.getKey().name(),
                    round(entry.getValue()));
        }

        Map<String, Double> floodExposure =
                new LinkedHashMap<>();

        for (Map.Entry<FloodHazardClass, Double> entry
                : floodKm.entrySet()) {

            floodExposure.put(
                    entry.getKey().name(),
                    round(entry.getValue()));
        }

        Map<String, Double> slopeExposure =
                new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry
                : slopeKm.entrySet()) {

            slopeExposure.put(
                    entry.getKey(),
                    round(entry.getValue()));
        }

        double approximateCompositeCostKm =
                totalKm
                        + lulcPenaltyKm
                        + floodPenaltyKm
                        + slopePenaltyKm;

        Map<String, Object> hardConstraints =
                new LinkedHashMap<>();

        hardConstraints.put(
                "protectedAreaViolationSamples",
                protectedAreaSamples);

        hardConstraints.put(
                "protectedAreaNoDataSamples",
                protectedAreaNoDataSamples);

        hardConstraints.put(
                "slopeOver30ViolationSamples",
                slopeOverLimitSamples);

        hardConstraints.put(
                "slopeNoDataViolationSamples",
                slopeNoDataSamples);

        hardConstraints.put(
                "allDiagnosticSamplesPass",
                protectedAreaSamples == 0
                        && protectedAreaNoDataSamples == 0
                        && slopeOverLimitSamples == 0
                        && slopeNoDataSamples == 0);

        Map<String, Object> diagnostics =
                new LinkedHashMap<>();

        diagnostics.put(
                "diagnosticIsApproximate",
                true);

        diagnostics.put(
                "sampleSpacingMeters",
                DIAGNOSTIC_SAMPLE_SPACING_METERS);

        diagnostics.put(
                "diagnosticSampleCount",
                diagnosticSamples);

        diagnostics.put(
                "routeLengthKm",
                round(totalKm));

        diagnostics.put(
                "lulcExposureKm",
                lulcExposure);

        diagnostics.put(
                "floodExposureKm",
                floodExposure);

        diagnostics.put(
                "slopeExposureKm",
                slopeExposure);

        diagnostics.put(
                "protectedAreaExposureKm",
                round(protectedAreaKm));

        diagnostics.put(
                "approximatePenaltyContributionKm",
                Map.of(
                        "LULC",
                        round(lulcPenaltyKm),
                        "FLOOD",
                        round(floodPenaltyKm),
                        "SLOPE",
                        round(slopePenaltyKm)));

        diagnostics.put(
                "approximateCompositeCostKmEquivalent",
                round(approximateCompositeCostKm));

        diagnostics.put(
                "floodCacheMissSamples",
                floodCacheMisses);

        diagnostics.put(
                "hardConstraintValidation",
                hardConstraints);

        return diagnostics;
    }

    private String classifySlope(
            Double slopeDegrees) {

        if (slopeDegrees == null) {
            return "SLOPE_NODATA";
        }

        if (slopeDegrees
                <= slopeProperties
                .getFreeMaxDegrees()) {

            return "SLOPE_FREE";
        }

        if (slopeDegrees
                <= slopeProperties
                .getLowMaxDegrees()) {

            return "SLOPE_LOW";
        }

        if (slopeDegrees
                <= slopeProperties
                .getModerateMaxDegrees()) {

            return "SLOPE_MODERATE";
        }

        if (slopeDegrees
                <= slopeProperties
                .getSteepMaxDegrees()) {

            return "SLOPE_STEEP";
        }

        if (slopeDegrees
                <= slopeProperties
                .getVerySteepMaxDegrees()) {

            return "SLOPE_VERY_STEEP";
        }

        return "SLOPE_OVER_LIMIT";
    }

    private double slopePenalty(
            Double slopeDegrees) {

        if (slopeDegrees == null) {
            return 0.0;
        }

        if (slopeDegrees
                <= slopeProperties
                .getFreeMaxDegrees()) {

            return 0.0;
        }

        if (slopeDegrees
                <= slopeProperties
                .getLowMaxDegrees()) {

            return slopeProperties
                    .getLowPenalty();
        }

        if (slopeDegrees
                <= slopeProperties
                .getModerateMaxDegrees()) {

            return slopeProperties
                    .getModeratePenalty();
        }

        if (slopeDegrees
                <= slopeProperties
                .getSteepMaxDegrees()) {

            return slopeProperties
                    .getSteepPenalty();
        }

        if (slopeDegrees
                <= slopeProperties
                .getVerySteepMaxDegrees()) {

            return slopeProperties
                    .getVerySteepPenalty();
        }

        return 0.0;
    }

    private double round(double value) {

        return Math.round(value * 100.0)
                / 100.0;
    }
}