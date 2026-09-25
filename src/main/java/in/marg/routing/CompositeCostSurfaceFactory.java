package in.marg.routing;

import in.marg.config.FloodRoutingProperties;
import in.marg.config.LulcRoutingProperties;
import in.marg.floodraster.BhuvanFloodRasterCache;
import in.marg.model.StudyRegion;
import in.marg.provider.LulcProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompositeCostSurfaceFactory {

    private final LulcProvider lulcProvider;
    private final LulcRoutingProperties lulcProperties;

    private final BhuvanFloodRasterCache floodRasterCache;
    private final FloodRoutingProperties floodProperties;

    private final SlopeCostLayer slopeCostLayer;
    private final ProtectedAreaCostLayer protectedAreaCostLayer;

    public CompositeCostSurfaceFactory(
            LulcProvider lulcProvider,
            LulcRoutingProperties lulcProperties,
            BhuvanFloodRasterCache floodRasterCache,
            FloodRoutingProperties floodProperties,
            SlopeCostLayer slopeCostLayer,
            ProtectedAreaCostLayer protectedAreaCostLayer) {

        this.lulcProvider = lulcProvider;
        this.lulcProperties = lulcProperties;
        this.floodRasterCache = floodRasterCache;
        this.floodProperties = floodProperties;
        this.slopeCostLayer = slopeCostLayer;
        this.protectedAreaCostLayer = protectedAreaCostLayer;
    }

    public BuildResult build(
            StudyRegion searchWindow,
            int maxLulcSamples) {

        // Explicitly prepare LULC before A*.
        var lulcSamples =
                lulcProvider.preload(
                        searchWindow,
                        maxLulcSamples);

        // Explicitly prepare flood raster before A*.
        var floodPreload =
                floodRasterCache.preload(
                        searchWindow.minLat(),
                        searchWindow.minLon(),
                        searchWindow.maxLat(),
                        searchWindow.maxLon(),
                        null,
                        null);

        CostLayer lulc =
                new LulcCostLayer(
                        lulcProvider,
                        lulcProperties.toPolicy());

        CostLayer flood =
                new FloodRasterCostLayer(
                        floodRasterCache,
                        floodProperties.toPolicy());

        CompositeCostSurface surface =
                new CompositeCostSurface(
                        List.of(
                                lulc,
                                flood,
                                slopeCostLayer,
                                protectedAreaCostLayer));

        return new BuildResult(
                surface,
                lulcSamples.size(),
                floodPreload);
    }

    public record BuildResult(
            CompositeCostSurface surface,
            int lulcSamplesLoaded,
            BhuvanFloodRasterCache.PreloadResult floodPreload) {
    }
}