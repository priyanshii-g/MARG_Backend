package in.marg.routing;

import in.marg.flood.FloodHazardClass;
import in.marg.floodraster.BhuvanFloodRasterCache;
import in.marg.floodraster.FloodRasterSample;
import in.marg.model.GeoPoint;

/** Deterministic flood cost lookup from the local Bhuvan WMS raster cache. */
public class FloodRasterCostLayer implements CostLayer {
    private static final String SOURCE = "bhuvan:flood-hazard-ras2-wms:local-raster-cache";
    private final BhuvanFloodRasterCache cache;
    private final FloodCostPolicy policy;

    public FloodRasterCostLayer(BhuvanFloodRasterCache cache, FloodCostPolicy policy) {
        this.cache = cache;
        this.policy = policy;
    }

    @Override
    public CostAssessment assess(GeoPoint point) {
        FloodRasterSample sample = cache.sample(point);
        if (!sample.cacheAvailable()) {
            throw new IllegalStateException("Flood raster cache miss at " + point
                    + ". Preload the routing search window before using FloodRasterCostLayer.");
        }
        FloodHazardClass hazard = sample.hazardClass();
        return new CostAssessment(false, policy.penalty(hazard), SOURCE, "FLOOD_" + hazard.name());
    }

    public FloodCostPolicy policy() {
        return policy;
    }
}
