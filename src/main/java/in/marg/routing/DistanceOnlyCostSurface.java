package in.marg.routing;

import in.marg.model.GeoPoint;

/** Pure geometric baseline: A* minimizes geographic movement distance only. */
public class DistanceOnlyCostSurface implements CostSurface {
    @Override
    public CostAssessment assess(GeoPoint point) {
        return CostAssessment.free("distance-only");
    }
}
