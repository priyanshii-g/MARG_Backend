package in.marg.routing;

import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import in.marg.spatial.Wgs84UtmProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenfieldAStarRouterTest {
    private final GreenfieldAStarRouter router = new GreenfieldAStarRouter(new Wgs84UtmProjection(46));

    @Test
    void distanceBaselineFindsAPath() {
        StudyRegion region = testRegion();
        List<GeoPoint> path = router.route(region, region.origin(), region.destination(),
                new DistanceOnlyCostSurface(), 250.0);

        assertTrue(path.size() >= 2);
        assertTrue(router.routeLengthKm(path) > 0.0);
    }

    @Test
    void projectedCoordinatesRoundTripWithinMillimetres() {
        var projection = new Wgs84UtmProjection(46);
        GeoPoint original = new GeoPoint(26.58970, 93.40035);
        GeoPoint roundTrip = projection.inverse(projection.forward(original));
        assertEquals(original.lat(), roundTrip.lat(), 1e-8);
        assertEquals(original.lon(), roundTrip.lon(), 1e-8);
    }

    @Test
    void constrainedSearchFindsAPathWithinPhysicalLengthBudget() {
        StudyRegion region = testRegion();
        List<GeoPoint> baselinePath = router.route(region, region.origin(), region.destination(),
                new DistanceOnlyCostSurface(), 250.0);
        double baseline = router.routeLengthKm(baselinePath);

        CostLayer syntheticForest = point -> {
            boolean band = point.lon() > 0.035 && point.lon() < 0.065 && point.lat() > 0.025 && point.lat() < 0.075;
            return band
                    ? new CostAssessment(false, 2.0, "synthetic-test", "FOREST")
                    : CostAssessment.free("synthetic-test");
        };

        double maxLength = baseline * 1.80;
        List<GeoPoint> path = assertDoesNotThrow(() -> router.route(
                region, region.origin(), region.destination(),
                new CompositeCostSurface(List.of(syntheticForest)),
                250.0, region, RouteConstraints.maxLengthKm(maxLength)));

        assertTrue(router.routeLengthKm(path) <= maxLength + 0.25);
    }

    @Test
    void constrainedSearchReturnsQuicklyWhenUnconstrainedRouteFitsBudget() {
        StudyRegion region = testRegion();
        CostLayer lowPenalty = point -> new CostAssessment(false, 0.5, "synthetic-test", "OPEN");
        List<GeoPoint> path = assertDoesNotThrow(() -> router.route(
                region, region.origin(), region.destination(),
                new CompositeCostSurface(List.of(lowPenalty)),
                250.0, region, RouteConstraints.maxLengthKm(20.0)));
        assertTrue(router.routeLengthKm(path) <= 20.25);
    }

    private StudyRegion testRegion() {
        return new StudyRegion(
                "unit-test",
                26.20, 93.20, 26.30, 93.30,
                new GeoPoint(26.21, 93.21),
                new GeoPoint(26.29, 93.29)
        );
    }
}
