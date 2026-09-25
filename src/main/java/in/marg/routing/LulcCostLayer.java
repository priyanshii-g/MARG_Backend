package in.marg.routing;

import in.marg.model.GeoPoint;
import in.marg.provider.LulcProvider;
import in.marg.provider.LulcSample;

/** Converts provider-supplied LULC classifications into routing penalties. */
public class LulcCostLayer implements CostLayer {
    private final LulcProvider provider;
    private final LulcCostPolicy policy;

    public LulcCostLayer(LulcProvider provider, LulcCostPolicy policy) {
        this.provider = provider;
        this.policy = policy;
    }

    @Override
    public CostAssessment assess(GeoPoint point) {
        LulcSample sample = provider.sample(point);
        LulcClass type = sample.type();
        return new CostAssessment(false, policy.penalty(type), sample.source(), type.name());
    }

    public LulcCostPolicy policy() {
        return policy;
    }
}
