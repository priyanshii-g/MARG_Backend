package in.marg.bhuvan;

import in.marg.model.GeoPoint;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Calls a Bhuvan WMS GetFeatureInfo request for a point. */
@Service
public class BhuvanFeatureInfoClient {
    private final BhuvanService bhuvanService;
    private final HttpClient client;

    public BhuvanFeatureInfoClient(BhuvanService bhuvanService) {
        this.bhuvanService = bhuvanService;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String query(BhuvanLayer layer, GeoPoint point, double halfSizeDeg) {
        URI uri = URI.create(bhuvanService.buildGetFeatureInfoUrl(
                layer.id(), point.lat(), point.lon(), halfSizeDeg, 101, 101, 50, 50));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "MARG-Prototype/0.2 (academic GIS research)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Bhuvan GetFeatureInfo HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bhuvan GetFeatureInfo interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Bhuvan GetFeatureInfo failed", e);
        }
    }
}
