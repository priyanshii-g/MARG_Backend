package in.marg.routing;

import in.marg.environment.ProtectedAreaRasterProvider;
import in.marg.model.GeoPoint;
import org.springframework.stereotype.Component;

@Component
public class ProtectedAreaCostLayer
        implements CostLayer {

    private final ProtectedAreaRasterProvider provider;

    public ProtectedAreaCostLayer(
            ProtectedAreaRasterProvider provider) {

        this.provider = provider;
    }

    @Override
    public CostAssessment assess(
            GeoPoint point) {

        Double maskValue =
                provider.sampleOrNull(
                        point.lat(),
                        point.lon());

        if (maskValue == null) {

            return CostAssessment.blocked(
                    "environment:protected-area",
                    "PROTECTED_AREA_NODATA");
        }

        if (Math.round(maskValue) == 1L) {

            return CostAssessment.blocked(
                    "environment:protected-area",
                    "PROTECTED_AREA");
        }

        return CostAssessment.free(
                "environment:protected-area");
    }
}