package in.marg.bhuvan;

public record BhuvanLayer(
        String id,
        String title,
        String serviceType,
        String serviceUrl,
        String layerName,
        String version,
        String crs,
        String format,
        String dataDate,
        String note
) {}
