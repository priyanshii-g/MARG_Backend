package in.marg.provider;

import in.marg.bhuvan.BhuvanLulcSampler;
import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import org.springframework.stereotype.Service;

import java.util.List;

/** Bhuvan-backed implementation of the MARG LULC provider contract. */
@Service
public class BhuvanLulcProvider implements LulcProvider {
    private static final String DATASET = "Assam LULC 1:50,000 2015-16";
    private final BhuvanLulcSampler sampler;

    public BhuvanLulcProvider(BhuvanLulcSampler sampler) {
        this.sampler = sampler;
    }

    @Override
    public LulcSample sample(GeoPoint point) {
        BhuvanLulcSampler.SampleResult result = sampler.sampleDetailed(point);
        return new LulcSample(result.lat(), result.lon(), result.type(), providerId(), result.remoteSuccess());
    }

    @Override
    public List<LulcSample> preload(StudyRegion region, int maxSamples) {
        return sampler.preload(region.minLat(), region.minLon(), region.maxLat(), region.maxLon(), maxSamples)
                .stream()
                .map(result -> new LulcSample(result.lat(), result.lon(), result.type(), providerId(), result.remoteSuccess()))
                .toList();
    }

    @Override
    public String providerId() {
        return "bhuvan:lulc-50k-2015-16";
    }

    @Override
    public double sampleResolution() {
        return sampler.sampleResolutionDeg();
    }

    @Override
    public String datasetId() {
        return DATASET;
    }

}
