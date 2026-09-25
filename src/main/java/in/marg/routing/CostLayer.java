package in.marg.routing;

import in.marg.model.GeoPoint;

/** One spatial factor contributing to the greenfield alignment cost surface. */
@FunctionalInterface
public interface CostLayer {
    CostAssessment assess(GeoPoint point);
}
