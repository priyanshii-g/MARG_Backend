package in.marg.bhuvan;

import in.marg.model.GeoPoint;
import in.marg.routing.LulcClass;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Samples Bhuvan's public LULC WMS using GetFeatureInfo and caches the classification
 * on a coarse geographic sample grid. The A* grid can then be finer than the remote
 * sampling grid without multiplying remote requests.
 */
@Service
public class BhuvanLulcSampler {
    private final BhuvanCatalog catalog;
    private final BhuvanFeatureInfoClient featureInfoClient;
    private final double sampleResolutionDeg;
    private final double queryHalfSizeDeg;
    private final int parallelism;
    private final java.nio.file.Path cacheFile;
    private final ObjectMapper objectMapper;
    private final Map<SampleKey, SampleResult> cache = new ConcurrentHashMap<>();

    public BhuvanLulcSampler(
            BhuvanCatalog catalog,
            BhuvanFeatureInfoClient featureInfoClient,
            @Value("${marg.bhuvan.lulc.sample-resolution-deg:0.02}") double sampleResolutionDeg,
            @Value("${marg.bhuvan.lulc.query-half-size-deg:0.006}") double queryHalfSizeDeg,
            @Value("${marg.bhuvan.lulc.parallelism:6}") int parallelism,
            @Value("${marg.bhuvan.lulc.cache-file:data/cache/bhuvan-lulc-samples.json}") String cacheFile,
            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.featureInfoClient = featureInfoClient;
        this.sampleResolutionDeg = sampleResolutionDeg;
        this.queryHalfSizeDeg = queryHalfSizeDeg;
        this.parallelism = Math.max(1, parallelism);
        this.cacheFile = java.nio.file.Paths.get(cacheFile);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadPersistentCache() {
        if (!java.nio.file.Files.exists(cacheFile)) return;
        try {
            List<PersistedSample> saved = objectMapper.readValue(
                    java.nio.file.Files.readString(cacheFile),
                    new TypeReference<List<PersistedSample>>() {});
            for (PersistedSample s : saved) {
                SampleResult result = new SampleResult(s.lat(), s.lon(), s.type(),
                        s.rawFeatureInfo() == null ? "" : s.rawFeatureInfo(),
                        s.remoteSuccess(), s.error());
                cache.put(new SampleKey(s.lat(), s.lon()), result);
            }
        } catch (Exception ignored) {
            // A corrupt/old cache must never prevent the application from starting.
        }
    }

    public LulcClass sample(GeoPoint point) {
        SampleKey key = key(point);
        return cache.computeIfAbsent(key, this::load).type();
    }

    public SampleResult sampleDetailed(GeoPoint point) {
        return cache.computeIfAbsent(key(point), this::load);
    }

    public int cacheSize() {
        return cache.size();
    }

    public List<SampleResult> preload(double minLat, double minLon, double maxLat, double maxLon, int maxSamples) {
        List<SampleKey> keys = new ArrayList<>();
        double firstLat = cellCenterAtOrAbove(minLat);
        double firstLon = cellCenterAtOrAbove(minLon);
        for (double lat = firstLat; lat <= maxLat + 1e-12; lat += sampleResolutionDeg) {
            for (double lon = firstLon; lon <= maxLon + 1e-12; lon += sampleResolutionDeg) {
                keys.add(new SampleKey(round(lat), round(lon)));
                if (keys.size() > maxSamples) {
                    throw new IllegalArgumentException("Requested Bhuvan LULC preload needs " + keys.size()
                            + " samples; increase sample resolution or corridor, or raise maxSamples.");
                }
            }
        }

        List<SampleResult> results = new ArrayList<>(keys.size());
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<SampleResult>> futures = new ArrayList<>();
            for (SampleKey key : keys) {
                futures.add(pool.submit(() -> cache.computeIfAbsent(key, this::load)));
            }
            for (Future<SampleResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    throw new IllegalStateException("Bhuvan LULC preload failed", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        persistCache();
        return results;
    }

    public double sampleResolutionDeg() {
        return sampleResolutionDeg;
    }

    public Map<String, Object> cacheStats() {
        long successes = cache.values().stream().filter(SampleResult::remoteSuccess).count();
        long unknown = cache.values().stream().filter(s -> s.type() == LulcClass.UNKNOWN).count();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (LulcClass type : LulcClass.values()) {
            long count = cache.values().stream().filter(s -> s.type() == type).count();
            if (count > 0) byType.put(type.name(), count);
        }
        return Map.of(
                "cachedSamples", cache.size(),
                "remoteSuccesses", successes,
                "unknownClassifications", unknown,
                "sampleResolutionDeg", sampleResolutionDeg,
                "cacheFile", cacheFile.toString(),
                "byLulcClass", byType
        );
    }

    private synchronized void persistCache() {
        try {
            java.nio.file.Files.createDirectories(cacheFile.getParent());
            List<PersistedSample> saved = cache.values().stream()
                    .map(s -> new PersistedSample(s.lat(), s.lon(), s.type(), s.rawFeatureInfo(), s.remoteSuccess(), s.error()))
                    .sorted(java.util.Comparator.comparingDouble(PersistedSample::lat).thenComparingDouble(PersistedSample::lon))
                    .toList();
            java.nio.file.Files.writeString(cacheFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(saved));
        } catch (Exception ignored) {
            // Cache persistence is an optimization; routing can continue without it.
        }
    }

    private SampleResult load(SampleKey key) {
        try {
            BhuvanLayer layer = catalog.get("assam-lulc-50k-1516");
            String raw = featureInfoClient.query(layer, new GeoPoint(key.lat(), key.lon()), queryHalfSizeDeg);
            LulcClass type = LulcClass.fromFeatureInfo(raw);
            return new SampleResult(key.lat(), key.lon(), type, raw, true, null);
        } catch (RuntimeException ex) {
            return new SampleResult(key.lat(), key.lon(), LulcClass.UNKNOWN, "", false, ex.getMessage());
        }
    }

    private SampleKey key(GeoPoint p) {
        return new SampleKey(round(cellCenter(p.lat())), round(cellCenter(p.lon())));
    }

    private double cellCenter(double value) {
        return (Math.floor(value / sampleResolutionDeg) + 0.5) * sampleResolutionDeg;
    }

    private double cellCenterAtOrAbove(double value) {
        double center = cellCenter(value);
        if (center < value - 1e-12) center += sampleResolutionDeg;
        return center;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    public record SampleResult(
            double lat,
            double lon,
            LulcClass type,
            String rawFeatureInfo,
            boolean remoteSuccess,
            String error
    ) {}

    private record SampleKey(double lat, double lon) {}

    private record PersistedSample(
            double lat,
            double lon,
            LulcClass type,
            String rawFeatureInfo,
            boolean remoteSuccess,
            String error
    ) {}
}
