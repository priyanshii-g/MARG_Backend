package in.marg.routing;

import in.marg.model.GeoPoint;

/** Composite spatial cost surface used by the greenfield A* router. */
@FunctionalInterface
public interface CostSurface {
    CostAssessment assess(GeoPoint point);
}
