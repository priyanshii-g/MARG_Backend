package in.marg.routing;

import in.marg.config.SlopeRoutingProperties;
import in.marg.model.GeoPoint;
import in.marg.terrain.SlopeRasterProvider;
import org.springframework.stereotype.Component;

@Component
public class SlopeCostLayer implements CostLayer {

    private final SlopeRasterProvider slopeRasterProvider;
    private final SlopeRoutingProperties properties;

    public SlopeCostLayer(
            SlopeRasterProvider slopeRasterProvider,
            SlopeRoutingProperties properties) {

        this.slopeRasterProvider = slopeRasterProvider;
        this.properties = properties;
    }

    @Override
    public CostAssessment assess(GeoPoint point) {

        Double slopeDegrees =
                slopeRasterProvider.sampleOrNull(
                        point.lat(),
                        point.lon());

        if (slopeDegrees == null) {

            if (properties.isBlockNoData()) {
                return new CostAssessment(
                        true,
                        0.0,
                        "terrain-slope",
                        "SLOPE_NODATA");
            }

            return new CostAssessment(
                    false,
                    0.0,
                    "terrain-slope",
                    "SLOPE_NODATA");
        }

        if (slopeDegrees <=
                properties.getFreeMaxDegrees()) {

            return new CostAssessment(
                    false,
                    0.0,
                    "terrain-slope",
                    "SLOPE_FREE");
        }

        if (slopeDegrees <=
                properties.getLowMaxDegrees()) {

            return new CostAssessment(
                    false,
                    properties.getLowPenalty(),
                    "terrain-slope",
                    "SLOPE_LOW");
        }

        if (slopeDegrees <=
                properties.getModerateMaxDegrees()) {

            return new CostAssessment(
                    false,
                    properties.getModeratePenalty(),
                    "terrain-slope",
                    "SLOPE_MODERATE");
        }

        if (slopeDegrees <=
                properties.getSteepMaxDegrees()) {

            return new CostAssessment(
                    false,
                    properties.getSteepPenalty(),
                    "terrain-slope",
                    "SLOPE_STEEP");
        }

        if (slopeDegrees <=
                properties.getVerySteepMaxDegrees()) {

            return new CostAssessment(
                    false,
                    properties.getVerySteepPenalty(),
                    "terrain-slope",
                    "SLOPE_VERY_STEEP");
        }

        if (properties.isBlockAboveVerySteep()) {

            return new CostAssessment(
                    true,
                    0.0,
                    "terrain-slope",
                    "SLOPE_OVER_LIMIT");
        }

        return new CostAssessment(
                false,
                properties.getVerySteepPenalty(),
                "terrain-slope",
                "SLOPE_OVER_LIMIT");
    }
}