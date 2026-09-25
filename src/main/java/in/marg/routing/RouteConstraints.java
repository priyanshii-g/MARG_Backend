package in.marg.routing;

/**
 * Optional global constraints applied while generating a greenfield alignment.
 * maxRouteLengthKm is a physical-length budget, not a cost budget.
 */
public record RouteConstraints(Double maxRouteLengthKm) {
    public static RouteConstraints none() {
        return new RouteConstraints(null);
    }

    public static RouteConstraints maxLengthKm(double maxRouteLengthKm) {
        if (!(maxRouteLengthKm > 0.0) || !Double.isFinite(maxRouteLengthKm)) {
            throw new IllegalArgumentException("maxRouteLengthKm must be a positive finite number");
        }
        return new RouteConstraints(maxRouteLengthKm);
    }
}
