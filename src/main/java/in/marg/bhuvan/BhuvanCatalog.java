package in.marg.bhuvan;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BhuvanCatalog {
    private final Map<String, BhuvanLayer> layers = Map.of(
            "assam-lulc-50k-1516", new BhuvanLayer(
                    "assam-lulc-50k-1516",
                    "Assam Land Use / Land Cover 1:50,000 (2015-16)",
                    "WMS",
                    "https://bhuvan-vec2.nrsc.gov.in/bhuvan/wms",
                    "lulc:AS_LULC50K_1516",
                    "1.1.1",
                    "EPSG:4326",
                    "image/png",
                    "2015-16",
                    "Bhuvan thematic layer; use for visualization and, after download/processing, as an input to the MARG cost surface."
            ),
            "assam-lulc-50k-1112", new BhuvanLayer(
                    "assam-lulc-50k-1112",
                    "Assam Land Use / Land Cover 1:50,000 (2011-12)",
                    "WMS",
                    "https://bhuvan-vec2.nrsc.gov.in/bhuvan/wms",
                    "lulc:AS_LULC50K_1112",
                    "1.1.1",
                    "EPSG:4326",
                    "image/png",
                    "2011-12",
                    "Historical LULC comparison layer."
            ),
            "assam-lulc-50k-0506", new BhuvanLayer(
                    "assam-lulc-50k-0506",
                    "Assam Land Use / Land Cover 1:50,000 (2005-06)",
                    "WMS",
                    "https://bhuvan-vec2.nrsc.gov.in/bhuvan/wms",
                    "lulc:AS_LULC50K_0506",
                    "1.1.1",
                    "EPSG:4326",
                    "image/png",
                    "2005-06",
                    "Historical LULC comparison layer."
            ),
            "assam-flood-hazard", new BhuvanLayer(
                    "assam-flood-hazard",
                    "Assam Flood Hazard",
                    "WMS",
                    "https://bhuvan-ras2.nrsc.gov.in/cgi-bin/hazard.exe",
                    "as_hz",
                    "1.1.1",
                    "EPSG:4326",
                    "image/png",
                    "Historical hazard product",
                    "Layer name is reported by publicly documented Bhuvan integrations; verify against the live service before treating it as an authoritative numeric mask."
            )
    );

    public List<BhuvanLayer> all() {
        return layers.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    public BhuvanLayer get(String id) {
        BhuvanLayer layer = layers.get(id);
        if (layer == null) {
            throw new IllegalArgumentException("Unknown Bhuvan layer: " + id);
        }
        return layer;
    }
}
