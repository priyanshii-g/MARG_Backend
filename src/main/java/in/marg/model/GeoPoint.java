package in.marg.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record GeoPoint(
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0") double lat,
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0") double lon
) {}
