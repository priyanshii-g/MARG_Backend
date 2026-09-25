package in.marg.api;

import in.marg.model.StudyRegion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-region")
public class StudyRegionController {
    private final StudyRegion region;

    public StudyRegionController(StudyRegion region) {
        this.region = region;
    }

    @GetMapping
    public Object get() {
        return java.util.Map.of(
                "name", region.name(),
                "bbox", java.util.Map.of(
                        "minLat", region.minLat(),
                        "minLon", region.minLon(),
                        "maxLat", region.maxLat(),
                        "maxLon", region.maxLon()
                ),
                "origin", region.origin(),
                "destination", region.destination(),
                "prototypeNote", "A/B and this window are a research prototype, not a construction proposal."
        );
    }
}
