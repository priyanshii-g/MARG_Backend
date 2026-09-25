package in.marg.bhuvan;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class BhuvanService {
    private final BhuvanCatalog catalog;
    private final HttpClient client;

    public BhuvanService(BhuvanCatalog catalog) {
        this.catalog = catalog;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String buildGetMapUrl(String layerId, double minLon, double minLat,
                                 double maxLon, double maxLat, int width, int height) {
        BhuvanLayer layer = catalog.get(layerId);
        return UriComponentsBuilder.fromUriString(layer.serviceUrl())
                .queryParam("service", "WMS")
                .queryParam("request", "GetMap")
                .queryParam("version", layer.version())
                .queryParam("layers", layer.layerName())
                .queryParam("styles", "")
                .queryParam("srs", layer.crs())
                .queryParam("bbox", "%s,%s,%s,%s".formatted(minLon, minLat, maxLon, maxLat))
                .queryParam("width", width)
                .queryParam("height", height)
                .queryParam("format", layer.format())
                .queryParam("transparent", "true")
                .build(true)
                .toUriString();
    }

    public String buildGetFeatureInfoUrl(String layerId, double lat, double lon,
                                         double halfSizeDeg, int width, int height, int x, int y) {
        BhuvanLayer layer = catalog.get(layerId);
        double minLon = lon - halfSizeDeg;
        double maxLon = lon + halfSizeDeg;
        double minLat = lat - halfSizeDeg;
        double maxLat = lat + halfSizeDeg;
        return UriComponentsBuilder.fromUriString(layer.serviceUrl())
                .queryParam("service", "WMS")
                .queryParam("request", "GetFeatureInfo")
                .queryParam("version", layer.version())
                .queryParam("layers", layer.layerName())
                .queryParam("query_layers", layer.layerName())
                .queryParam("styles", "")
                .queryParam("srs", layer.crs())
                .queryParam("bbox", "%s,%s,%s,%s".formatted(minLon, minLat, maxLon, maxLat))
                .queryParam("width", width)
                .queryParam("height", height)
                .queryParam("format", "image/png")
                .queryParam("info_format", "text/plain")
                .queryParam("feature_count", 1)
                .queryParam("x", x)
                .queryParam("y", y)
                .build(true)
                .toUriString();
    }

    public ResponseEntity<byte[]> fetchMap(String layerId, double minLon, double minLat,
                                            double maxLon, double maxLat, int width, int height) {
        try {
            URI uri = URI.create(buildGetMapUrl(layerId, minLon, minLat, maxLon, maxLat, width, height));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "MARG-Prototype/0.2 (academic GIS research)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return ResponseEntity.status(response.statusCode()).body(response.body());
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=300")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(502).body(("Bhuvan request interrupted: " + e.getMessage()).getBytes());
        } catch (IOException | IllegalArgumentException e) {
            return ResponseEntity.status(502).body(("Bhuvan request failed: " + e.getMessage()).getBytes());
        }
    }
}
