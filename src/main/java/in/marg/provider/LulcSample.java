package in.marg.provider;

import in.marg.routing.LulcClass;

/** A normalized point sample returned by a LULC data provider. */
public record LulcSample(
        double lat,
        double lon,
        LulcClass type,
        String source,
        boolean remoteSuccess
) {}
