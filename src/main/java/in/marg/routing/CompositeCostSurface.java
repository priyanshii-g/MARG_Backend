package in.marg.routing;

import in.marg.model.GeoPoint;

import java.util.List;

/** Combines independent spatial layers without coupling the A* implementation to GIS details. */
public class CompositeCostSurface implements CostSurface {
    private final List<CostLayer> layers;

    public CompositeCostSurface(List<CostLayer> layers) {
        this.layers = List.copyOf(layers);
    }

    @Override
    public CostAssessment assess(GeoPoint point) {
        double penalty = 0.0;
        String source = "";
        String className = null;
        for (CostLayer layer : layers) {
            CostAssessment a = layer.assess(point);
            if (a.blocked()) {
                return a;
            }
            penalty += Math.max(0.0, a.penaltyMultiplier());
            if (className == null && a.className() != null) {
                className = a.className();
            }
            if (!a.source().isBlank()) {
                source = source.isBlank() ? a.source() : source + "," + a.source();
            }
        }
        return new CostAssessment(false, penalty, source, className);
    }
}
