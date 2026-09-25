package in.marg.environment;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.Position2D;
import org.geotools.referencing.CRS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.geotools.api.coverage.PointOutsideCoverageException;

import java.io.File;

@Component
public class ProtectedAreaRasterProvider {

    private final String rasterPath;

    private GridCoverage2D coverage;
    private org.geotools.api.referencing.operation.MathTransform
            wgs84ToRaster;
    private CoordinateReferenceSystem wgs84;

    public ProtectedAreaRasterProvider(
            @Value("${marg.protected-area.raster-path:data/processed/protected-areas/assam_protected_area_mask_utm46.tif}")
            String rasterPath) {

        this.rasterPath = rasterPath;
    }

    private synchronized void ensureInitialized() {

        if (coverage != null
                && wgs84ToRaster != null) {
            return;
        }

        try {

            File file = new File(rasterPath);

            if (!file.exists()) {
                throw new IllegalStateException(
                        "Protected-area raster does not exist: "
                                + file.getAbsolutePath());
            }

            GeoTiffReader reader =
                    new GeoTiffReader(file);

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

            wgs84 =
                    CRS.decode(
                            "EPSG:4326",
                            true);

            CoordinateReferenceSystem rasterCrs =
                    coverage.getCoordinateReferenceSystem2D();

            wgs84ToRaster =
                    CRS.findMathTransform(
                            wgs84,
                            rasterCrs,
                            true);

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to initialize protected-area raster: "
                            + rasterPath,
                    e);
        }
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

            var target =
                    wgs84ToRaster.transform(
                            source,
                            null);

            double[] values =
                    coverage.evaluate(
                            target,
                            new double[1]);

            if (values == null
                    || values.length == 0) {
                return null;
            }

            double value = values[0];

            // Teammate mask: 255 = NoData.
            if (Double.isNaN(value)
                    || Double.isInfinite(value)
                    || value == 255.0) {
                return null;
            }

            if (value != 0.0
                    && value != 1.0) {

                throw new IllegalStateException(
                        "Unexpected protected-area mask value: "
                                + value
                                + " at lat="
                                + latitude
                                + ", lon="
                                + longitude);
            }

            return value;

        } catch (PointOutsideCoverageException e) {
                return null;
        } catch (IllegalStateException e) {
            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to sample protected-area mask at lat="
                            + latitude
                            + ", lon="
                            + longitude,
                    e);
        }
    }

    public String rasterPath() {
        return rasterPath;
    }
}