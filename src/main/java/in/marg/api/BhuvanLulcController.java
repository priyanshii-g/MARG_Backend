package in.marg.api;

import in.marg.bhuvan.BhuvanLulcSampler;
import in.marg.model.GeoPoint;
import in.marg.model.StudyRegion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bhuvan/lulc")
public class BhuvanLulcController {
    private final BhuvanLulcSampler sampler;
    private final StudyRegion region;

    public BhuvanLulcController(BhuvanLulcSampler sampler, StudyRegion region) {
        this.sampler = sampler;
        this.region = region;
    }

    @GetMapping("/sample")
    public Object sample(@RequestParam double lat, @RequestParam double lon) {
        if (!region.contains(new GeoPoint(lat, lon))) {
            throw new IllegalArgumentException("Point is outside the configured study region");
        }
        return sampler.sampleDetailed(new GeoPoint(lat, lon));
    }

    @GetMapping("/check")
    public Object check() {
        var a = sampler.sampleDetailed(region.origin());
        var b = sampler.sampleDetailed(region.destination());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", "Assam LULC 1:50,000 2015-16");
        result.put("originSample", a);
        result.put("destinationSample", b);
        result.put("readyForLulcRouting", a.remoteSuccess() || b.remoteSuccess());
        result.put("cachedSamples", sampler.cacheSize());
        result.put("sampleResolutionDeg", sampler.sampleResolutionDeg());
        return result;
    }

    @GetMapping("/cache")
    public Object cache() {
        return sampler.cacheStats();
    }
}
