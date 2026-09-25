package in.marg.api;

import in.marg.bhuvan.BhuvanCatalog;
import in.marg.bhuvan.BhuvanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bhuvan")
public class BhuvanController {
    private final BhuvanCatalog catalog;
    private final BhuvanService service;

    public BhuvanController(BhuvanCatalog catalog, BhuvanService service) {
        this.catalog = catalog;
        this.service = service;
    }

    @GetMapping("/layers")
    public Object layers() {
        return catalog.all();
    }

    @GetMapping("/layers/{layerId}")
    public Object layer(@PathVariable String layerId) {
        return catalog.get(layerId);
    }

    @GetMapping("/map/{layerId}")
    public ResponseEntity<byte[]> map(
            @PathVariable String layerId,
            @RequestParam double minLon,
            @RequestParam double minLat,
            @RequestParam double maxLon,
            @RequestParam double maxLat,
            @RequestParam(defaultValue = "1200") int width,
            @RequestParam(defaultValue = "900") int height
    ) {
        if (width < 256 || width > 2500 || height < 256 || height > 2500) {
            return ResponseEntity.badRequest().build();
        }
        validateBbox(minLon, minLat, maxLon, maxLat);
        return service.fetchMap(layerId, minLon, minLat, maxLon, maxLat, width, height);
    }

    @GetMapping("/map-url/{layerId}")
    public Object mapUrl(
            @PathVariable String layerId,
            @RequestParam double minLon,
            @RequestParam double minLat,
            @RequestParam double maxLon,
            @RequestParam double maxLat,
            @RequestParam(defaultValue = "1200") int width,
            @RequestParam(defaultValue = "900") int height
    ) {
        validateBbox(minLon, minLat, maxLon, maxLat);
        return java.util.Map.of(
                "layer", catalog.get(layerId),
                "getMapUrl", service.buildGetMapUrl(layerId, minLon, minLat, maxLon, maxLat, width, height)
        );
    }

    private void validateBbox(double minLon, double minLat, double maxLon, double maxLat) {
        if (minLon >= maxLon || minLat >= maxLat) {
            throw new IllegalArgumentException("Invalid bbox: expected min < max for both longitude and latitude");
        }
        if (maxLon - minLon > 2.0 || maxLat - minLat > 2.0) {
            throw new IllegalArgumentException("BBox too large for the prototype endpoint");
        }
    }
}
