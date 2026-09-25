package in.marg.terrain;

import jakarta.annotation.PostConstruct;
import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.Position2D;
import org.geotools.referencing.CRS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class SlopeRasterProvider {

    private final String rasterPath;

    private GridCoverage2D coverage;
    private MathTransform wgs84ToRaster;
    private CoordinateReferenceSystem wgs84;

    public SlopeRasterProvider(
            @Value("${marg.terrain.slope-path:data/processed/dem/assam_slope_utm46.tif}")
            String rasterPath) {
        this.rasterPath = rasterPath;
    }

    private synchronized void ensureInitialized() {
        if (coverage != null && wgs84ToRaster != null) {
            return;
        }

        File file = new File(rasterPath);

        if (!file.exists()) {
            throw new IllegalStateException(
                    "Slope raster not found: " + file.getAbsolutePath());
        }

        try {
            GeoTiffReader reader = new GeoTiffReader(file);

            try {
                coverage = reader.read();
            } finally {
                reader.dispose();
            }

            if (coverage == null) {
                throw new IllegalStateException(
                        "GeoTIFF reader returned no coverage: "
                                + file.getAbsolutePath());
            }

            CoordinateReferenceSystem rasterCrs =
                    coverage.getCoordinateReferenceSystem2D();

            wgs84 = CRS.decode("EPSG:4326", true);

            wgs84ToRaster = CRS.findMathTransform(
                    wgs84,
                    rasterCrs,
                    true);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load slope raster: "
                            + file.getAbsolutePath(),
                    e);
        }
    }

    public double sample(
            double latitude,
            double longitude) {

        Double value = sampleOrNull(latitude, longitude);

        if (value == null) {
            throw new IllegalStateException(
                    "NoData slope value at "
                            + latitude + ", " + longitude);
        }

        return value;
    }

    public Double sampleOrNull(
            double latitude,
            double longitude) {

        ensureInitialized();

        try {
            Position2D source =
                    new Position2D(
                            wgs84,
                            longitude,
                            latitude);

            Position2D target =
                    new Position2D();

            wgs84ToRaster.transform(
                    source,
                    target);

            Position position = target;

            double[] values =
                    coverage.evaluate(
                            position,
                            new double[1]);

            if (values == null
                    || values.length == 0) {
                return null;
            }

            double slopeDegrees = values[0];

            if (slopeDegrees == -9999.0
                    || Double.isNaN(slopeDegrees)
                    || Double.isInfinite(slopeDegrees)) {
                return null;
            }

            if (slopeDegrees < 0.0
                    || slopeDegrees > 90.0) {
                throw new IllegalStateException(
                        "Invalid slope value "
                                + slopeDegrees
                                + " at "
                                + latitude
                                + ", "
                                + longitude);
            }

            return slopeDegrees;

        } catch (IllegalStateException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to sample slope at "
                            + latitude
                            + ", "
                            + longitude,
                    e);
        }
    }

    public String rasterPath() {
        return rasterPath;
    }
}