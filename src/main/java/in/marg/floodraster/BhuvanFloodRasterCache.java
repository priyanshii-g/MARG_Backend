package in.marg.floodraster;

import in.marg.flood.FloodHazardClass;
import in.marg.model.GeoPoint;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local cache of Bhuvan hazard.exe WMS rasters. No remote request occurs while
 * routing; remote work is performed only by an explicit preload call.
 */
@Service
public class BhuvanFloodRasterCache {
    private final BhuvanFloodWmsRasterClient client;
    private final Path cacheDirectory;
    private final ObjectMapper objectMapper;
    private final int tilePixels;
    private final int targetPixelsPerDegree;
    private final int colorTolerance;
    private final int classificationWindowRadius;
    private final List<RasterMosaic> mosaics = new ArrayList<>();

    public BhuvanFloodRasterCache(
            BhuvanFloodWmsRasterClient client,
            @Value("${marg.bhuvan.flood-raster.cache-directory:data/cache/bhuvan-flood-wms}") String cacheDirectory,
            @Value("${marg.bhuvan.flood-raster.tile-pixels:512}") int tilePixels,
            @Value("${marg.bhuvan.flood-raster.target-pixels-per-degree:1000}") int targetPixelsPerDegree,
            @Value("${marg.bhuvan.flood-raster.color-tolerance:28}") int colorTolerance,
            @Value("${marg.bhuvan.flood-raster.classification-window-radius:1}") int classificationWindowRadius,
            ObjectMapper objectMapper) {
        this.client = client;
        this.cacheDirectory = Paths.get(cacheDirectory);
        this.objectMapper = objectMapper;
        this.tilePixels = clamp(tilePixels, 128, 1024);
        this.targetPixelsPerDegree = clamp(targetPixelsPerDegree, 250, 2500);
        this.colorTolerance = clamp(colorTolerance, 5, 80);
        this.classificationWindowRadius = clamp(classificationWindowRadius, 0, 3);
    }

    @PostConstruct
    void load() {
        if (!Files.isDirectory(cacheDirectory)) return;
        try (var stream = Files.list(cacheDirectory)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(this::loadManifest);
        } catch (Exception ignored) {
            // Corrupt/partial cache must not prevent startup.
        }
    }

    public synchronized PreloadResult preload(double minLat, double minLon, double maxLat, double maxLon,
                                               Integer requestedWidth, Integer requestedHeight) {
        validateBbox(minLat, minLon, maxLat, maxLon);
        int width = requestedWidth == null ? Math.max(512, round((maxLon - minLon) * targetPixelsPerDegree)) : requestedWidth;
        int height = requestedHeight == null ? Math.max(512, round((maxLat - minLat) * targetPixelsPerDegree)) : requestedHeight;
        width = clamp(width, 512, 2500);
        height = clamp(height, 512, 2500);

        RasterMosaic reusable = mosaics.stream()
                .filter(m -> m.contains(minLat, minLon, maxLat, maxLon))
                .findFirst().orElse(null);
        if (reusable != null) {
            return new PreloadResult(true, false, reusable, 0, "Existing local raster already covers requested bbox");
        }

        int cols = (int) Math.ceil(width / (double) tilePixels);
        int rows = (int) Math.ceil(height / (double) tilePixels);
        int actualWidth = cols * tilePixels;
        int actualHeight = rows * tilePixels;
        BufferedImage mosaic = new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = mosaic.createGraphics();
        int requests = 0;
        try {
            for (int row = 0; row < rows; row++) {
                int y0 = row * tilePixels;
                int y1 = Math.min((row + 1) * tilePixels, actualHeight);
                double tileMinLat = maxLat - (maxLat - minLat) * y1 / actualHeight;
                double tileMaxLat = maxLat - (maxLat - minLat) * y0 / actualHeight;
                for (int col = 0; col < cols; col++) {
                    int x0 = col * tilePixels;
                    int x1 = Math.min((col + 1) * tilePixels, actualWidth);
                    double tileMinLon = minLon + (maxLon - minLon) * x0 / actualWidth;
                    double tileMaxLon = minLon + (maxLon - minLon) * x1 / actualWidth;
                    var response = client.fetch(tileMinLon, tileMinLat, tileMaxLon, tileMaxLat,
                            x1 - x0, y1 - y0);
                    graphics.drawImage(response.image(), x0, y0, x1 - x0, y1 - y0, null);
                    requests++;
                }
            }
        } finally {
            graphics.dispose();
        }

        String key = hash("%.6f|%.6f|%.6f|%.6f|%d|%d".formatted(minLon, minLat, maxLon, maxLat, actualWidth, actualHeight));
        try {
            Files.createDirectories(cacheDirectory);
            Path imageFile = cacheDirectory.resolve("flood-hazard-" + key + ".png");
            Path manifestFile = cacheDirectory.resolve("flood-hazard-" + key + ".json");
            ImageIO.write(mosaic, "png", imageFile.toFile());
            Manifest manifest = new Manifest(key, imageFile.getFileName().toString(), minLon, minLat,
                    maxLon, maxLat, actualWidth, actualHeight, requests,
                    BhuvanFloodWmsRasterClient.SERVICE_URL, BhuvanFloodWmsRasterClient.LAYER_NAME,
                    "EPSG:4326", "1:250,000", colorTolerance, classificationWindowRadius);
            Files.writeString(manifestFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            RasterMosaic loaded = new RasterMosaic(manifest, mosaic, imageFile);
            mosaics.removeIf(m -> m.id().equals(key));
            mosaics.add(loaded);
            return new PreloadResult(false, true, loaded, requests, "Bhuvan hazard.exe raster mosaic downloaded and cached locally");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist Bhuvan flood raster cache: " + e.getMessage(), e);
        }
    }

    public synchronized RasterMosaic findCovering(GeoPoint point) {
        return mosaics.stream()
                .filter(m -> m.contains(point.lat(), point.lon()))
                .max(Comparator.comparingDouble(RasterMosaic::pixelsPerDegree))
                .orElse(null);
    }

    public synchronized RasterMosaic findLargestCovering(double minLat, double minLon, double maxLat, double maxLon) {
        return mosaics.stream()
                .filter(m -> m.contains(minLat, minLon, maxLat, maxLon))
                .max(Comparator.comparingDouble(RasterMosaic::areaDeg))
                .orElse(null);
    }

    public FloodRasterSample sample(GeoPoint point) {
        RasterMosaic mosaic = findCovering(point);
        if (mosaic == null) {
            return FloodRasterSample.cacheMiss(point);
        }
        return mosaic.sample(point, colorTolerance, classificationWindowRadius);
    }

    public synchronized List<Map<String, Object>> metadata() {
        return mosaics.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.id());
            item.put("bbox", Map.of("minLat", m.minLat(), "minLon", m.minLon(), "maxLat", m.maxLat(), "maxLon", m.maxLon()));
            item.put("width", m.width());
            item.put("height", m.height());
            item.put("pixelsPerDegree", m.pixelsPerDegree());
            item.put("source", m.sourceUrl());
            item.put("layer", m.layerName());
            item.put("sourceResolution", m.sourceResolution());
            item.put("imageEndpoint", "/api/flood/raster/image?id=" + m.id());
            item.put("classifiedPixelCounts", m.classifiedPixelCounts());
            item.put("unknownPixelCount", m.unknownPixelCount());
            item.put("noDataPixelCount", m.noDataPixelCount());
            return item;
        }).toList();
    }

    public synchronized RasterMosaic byId(String id) {
        return mosaics.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    public Map<String, Object> stats() {
        List<Map<String, Object>> items = metadata();
        long classified = 0;
        long unknown = 0;
        long noData = 0;
        for (Map<String, Object> item : items) {
            classified += ((Map<?, ?>) item.get("classifiedPixelCounts")).values().stream().mapToLong(v -> ((Number) v).longValue()).sum();
            unknown += ((Number) item.get("unknownPixelCount")).longValue();
            noData += ((Number) item.get("noDataPixelCount")).longValue();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cachedMosaics", items.size());
        result.put("classifiedPixels", classified);
        result.put("unknownPixels", unknown);
        result.put("noDataPixels", noData);
        result.put("tilePixels", tilePixels);
        result.put("targetPixelsPerDegree", targetPixelsPerDegree);
        result.put("colorTolerance", colorTolerance);
        result.put("classificationWindowRadius", classificationWindowRadius);
        result.put("source", BhuvanFloodWmsRasterClient.SERVICE_URL);
        result.put("layer", BhuvanFloodWmsRasterClient.LAYER_NAME);
        result.put("sourceResolution", "1:250,000");
        return result;
    }

    private void loadManifest(Path manifestFile) {
        try {
            Manifest manifest = objectMapper.readValue(Files.readString(manifestFile), new TypeReference<Manifest>() {});
            Path imageFile = cacheDirectory.resolve(manifest.imageFile());
            if (!Files.exists(imageFile)) return;
            BufferedImage image = ImageIO.read(imageFile.toFile());
            if (image == null) return;
            mosaics.add(new RasterMosaic(manifest, image, imageFile));
        } catch (Exception ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private static void validateBbox(double minLat, double minLon, double maxLat, double maxLon) {
        if (minLat >= maxLat || minLon >= maxLon) throw new IllegalArgumentException("Invalid bbox");
        if (minLat < -90 || maxLat > 90 || minLon < -180 || maxLon > 180) throw new IllegalArgumentException("Invalid WGS84 bbox");
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) b.append("%02x".formatted(bytes[i] & 0xff));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public record PreloadResult(boolean reused, boolean downloaded, RasterMosaic mosaic,
                                int remoteRequests, String message) {}

    public record Manifest(String id, String imageFile,
                           double minLon, double minLat, double maxLon, double maxLat,
                           int width, int height, int remoteRequests,
                           String sourceUrl, String layerName, String crs,
                           String sourceResolution, int colorTolerance, int windowRadius) {}

    public static final class RasterMosaic {
        private final Manifest manifest;
        private final BufferedImage image;
        private final Path imageFile;
        private volatile Map<String, Long> classCounts;
        private volatile long unknownCount;
        private volatile long noDataCount;

        RasterMosaic(Manifest manifest, BufferedImage image, Path imageFile) {
            this.manifest = manifest;
            this.image = image;
            this.imageFile = imageFile;
            computeCounts();
        }

        public String id() { return manifest.id(); }
        public String imageFileName() { return manifest.imageFile(); }
        public double minLon() { return manifest.minLon(); }
        public double minLat() { return manifest.minLat(); }
        public double maxLon() { return manifest.maxLon(); }
        public double maxLat() { return manifest.maxLat(); }
        public int width() { return manifest.width(); }
        public int height() { return manifest.height(); }
        public int remoteRequests() { return manifest.remoteRequests(); }
        public String sourceUrl() { return manifest.sourceUrl(); }
        public String layerName() { return manifest.layerName(); }
        public String sourceResolution() { return manifest.sourceResolution(); }
        public Path imageFile() { return imageFile; }
        public double pixelsPerDegree() { return Math.max(width() / (maxLon() - minLon()), height() / (maxLat() - minLat())); }
        public double areaDeg() { return (maxLon() - minLon()) * (maxLat() - minLat()); }
        public Map<String, Long> classifiedPixelCounts() { return classCounts; }
        public long unknownPixelCount() { return unknownCount; }
        public long noDataPixelCount() { return noDataCount; }

        public boolean contains(double lat, double lon) {
            return lon >= minLon() && lon <= maxLon() && lat >= minLat() && lat <= maxLat();
        }

        public boolean contains(double minLat, double minLon, double maxLat, double maxLon) {
            return minLat >= minLat() && maxLat <= maxLat() && minLon >= minLon() && maxLon <= maxLon();
        }

        public FloodRasterSample sample(GeoPoint point) {
            return sample(point, manifest.colorTolerance(), manifest.windowRadius());
        }

        public FloodRasterSample sample(GeoPoint point, int tolerance, int radius) {
            double fx = (point.lon() - minLon()) / (maxLon() - minLon()) * width();
            double fy = (maxLat() - point.lat()) / (maxLat() - minLat()) * height();
            int px = Math.max(0, Math.min(width() - 1, (int) Math.floor(fx)));
            int py = Math.max(0, Math.min(height() - 1, (int) Math.floor(fy)));

            List<FloodHazardClass> observed = new ArrayList<>();
            boolean sawUnknown = false;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int x = px + dx;
                    int y = py + dy;
                    if (x < 0 || x >= width() || y < 0 || y >= height()) continue;
                    FloodHazardClass c = FloodRasterColorClassifier.classify(image.getRGB(x, y), tolerance);
                    if (c == FloodHazardClass.UNKNOWN) sawUnknown = true;
                    if (c != FloodHazardClass.NOT_IN_HAZARD_LAYER && c != FloodHazardClass.UNKNOWN) observed.add(c);
                }
            }

            if (observed.isEmpty() && sawUnknown) {
                return new FloodRasterSample(point.lat(), point.lon(), FloodHazardClass.UNKNOWN,
                        true, "UNKNOWN_RASTER_COLOR", "Raster pixel color did not match any published Bhuvan legend color within tolerance.",
                        px, py, List.of(), "NONE", id(), imageFile.toString());
            }
            if (observed.isEmpty()) {
                return new FloodRasterSample(point.lat(), point.lon(), FloodHazardClass.NOT_IN_HAZARD_LAYER,
                        true, "NO_HAZARD_FEATURE", null, px, py, List.of(), "NONE", id(), imageFile.toString());
            }
            List<String> observedNames = observed.stream().map(Enum::name).distinct().sorted(Comparator.comparingInt(n -> FloodHazardClass.valueOf(n).publishedCategoryWeight())).toList();
            FloodHazardClass selected = FloodHazardClass.conservativeMax(observed);
            String method = observedNames.size() == 1 ? "SINGLE_RASTER_CLASS" : "CONSERVATIVE_MAX";
            String status = observedNames.size() == 1 ? "CLASSIFIED_RASTER" : "CLASSIFIED_CONSERVATIVE_MAX";
            String error = observedNames.size() > 1 ? "Multiple hazard colors occurred inside the local raster sampling window; MARG uses the highest observed category for conservative screening." : null;
            return new FloodRasterSample(point.lat(), point.lon(), selected, true, status, error,
                    px, py, observedNames, method, id(), imageFile.toString());
        }

        private void computeCounts() {
            Map<String, Long> counts = new LinkedHashMap<>();
            long unknown = 0;
            long noData = 0;
            int width = image.getWidth();
            int height = image.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >>> 24) & 0xff;
                    if (alpha < 10) { noData++; continue; }
                    FloodHazardClass c = FloodRasterColorClassifier.classify(argb, manifest.colorTolerance());
                    if (c == FloodHazardClass.UNKNOWN) unknown++;
                    else if (c != FloodHazardClass.NOT_IN_HAZARD_LAYER) counts.merge(c.name(), 1L, Long::sum);
                    else noData++;
                }
            }
            this.classCounts = Map.copyOf(counts);
            this.unknownCount = unknown;
            this.noDataCount = noData;
        }
    }
}
