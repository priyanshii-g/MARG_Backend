package in.marg.provider;

import in.marg.model.StudyRegion;

import java.util.List;

/** LULC-specific provider contract consumed by routing and diagnostics. */
public interface LulcProvider extends SpatialProvider<LulcSample> {
    List<LulcSample> preload(StudyRegion region, int maxSamples);

    double sampleResolution();

    String datasetId();
}
