package in.marg.floodraster;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Fetches the official Bhuvan/NRSC Flood Hazard WMS raster (hazard.exe). */
@Service
public class BhuvanFloodWmsRasterClient {
    public static final String SERVICE_URL = "https://bhuvan-ras2.nrsc.gov.in/cgi-bin/hazard.exe";
    public static final String LAYER_NAME = "as_hz";
    public static final String CRS = "EPSG:4326";
    public static final String FORMAT = "image/png";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public RasterResponse fetch(double minLon, double minLat, double maxLon, double maxLat,
                                int width, int height) {
        URI uri = UriComponentsBuilder.fromUriString(SERVICE_URL)
                .queryParam("service", "WMS")
                .queryParam("version", "1.1.1")
                .queryParam("request", "GetMap")
                .queryParam("layers", LAYER_NAME)
                .queryParam("styles", "")
                .queryParam("srs", CRS)
                .queryParam("bbox", "%s,%s,%s,%s".formatted(minLon, minLat, maxLon, maxLat))
                .queryParam("width", width)
                .queryParam("height", height)
                .queryParam("format", FORMAT)
                .queryParam("transparent", "true")
                .build(true)
                .toUri();

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(90))
                    .header("User-Agent", "MARG-Prototype/0.5.11 (academic GIS research)")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Bhuvan Flood WMS HTTP " + response.statusCode());
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IllegalStateException("Bhuvan Flood WMS returned a non-image response");
            }
            return new RasterResponse(uri.toString(), image);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bhuvan Flood WMS request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Bhuvan Flood WMS request failed: " + e.getMessage(), e);
        }
    }

    public record RasterResponse(String requestUrl, BufferedImage image) {}
}
