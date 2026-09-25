package in.marg.routing;

import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import in.marg.spatial.Wgs84UtmProjection;

import java.util.List;

/** Optional manual smoke test. Spring/Maven is the normal application entry point. */
public class RouterSmokeTest {
    public static void main(String[] args) {
        StudyRegion r = new StudyRegion(
                "Kaziranga–Karbi Anglong, Assam",
                25.75, 93.10, 26.65, 93.75,
                new GeoPoint(26.58970, 93.40035),
                new GeoPoint(25.84573, 93.43781)
        );
        GreenfieldAStarRouter router = new GreenfieldAStarRouter(new Wgs84UtmProjection(46));
        List<GeoPoint> distance = router.route(r, r.origin(), r.destination(),
                new DistanceOnlyCostSurface(), 250.0);
        System.out.printf("DISTANCE: %.2f km, %d vertices%n", router.routeLengthKm(distance), distance.size());

        CostLayer forest = p -> {
            boolean forestBand = p.lon() < 93.25 && p.lat() < 26.35 && p.lat() > 25.95;
            return forestBand ? new CostAssessment(false, 2.0, "synthetic-forest-test", "FOREST")
                    : CostAssessment.free("synthetic-forest-test");
        };
        List<GeoPoint> constrained = router.route(r, r.origin(), r.destination(),
                new CompositeCostSurface(List.of(forest)), 250.0);
        System.out.printf("SYNTHETIC FOREST: %.2f km, %d vertices%n", router.routeLengthKm(constrained), constrained.size());
    }
}
