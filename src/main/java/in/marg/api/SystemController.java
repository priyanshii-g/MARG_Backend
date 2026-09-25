package in.marg.api;

import in.marg.spatial.CoordinateProjection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final CoordinateProjection projection;

    public SystemController(CoordinateProjection projection) {
        this.projection = projection;
    }

    @GetMapping
    public Map<String, Object> system() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", "MARG");
        result.put("prototypeVersion", "0.5.0");
        result.put("javaRuntime", System.getProperty("java.version"));
        result.put("javaMajor", Runtime.version().feature());
        result.put("routingModel", "greenfield metric-grid A*");
        result.put("coordinateReferenceSystem", projection.crs());
        result.put("bhuvanLulc", "Assam LULC 1:50,000 2015-16");
        result.put("dataArchitecture", "typed spatial data providers");
        result.put("lulcProvider", "bhuvan:lulc-50k-2015-16");
        return result;
    }
}
