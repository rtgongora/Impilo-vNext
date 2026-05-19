package zw.gov.mohcc.impilo.ndila.core.location;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight coordinate validator. Checks range, accuracy, presence and a few
 * country-specific (Zimbabwe) sanity rules. Used by the location validation
 * endpoint and by spatial intelligence to flag implausible coordinates.
 */
@Service
public class NdilaCoordinateValidator {

    // Approximate Zimbabwe bounding box.
    private static final double ZW_LAT_MIN = -22.5;
    private static final double ZW_LAT_MAX = -15.6;
    private static final double ZW_LNG_MIN =  25.2;
    private static final double ZW_LNG_MAX =  33.1;

    public Result validate(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters, String country) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (latitude == null || longitude == null) {
            errors.add("MISSING_COORDINATES");
            return new Result(false, false, errors, warnings, 0.0);
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        if (lat < -90 || lat > 90) errors.add("LATITUDE_OUT_OF_RANGE");
        if (lng < -180 || lng > 180) errors.add("LONGITUDE_OUT_OF_RANGE");
        if (!errors.isEmpty()) return new Result(false, false, errors, warnings, 0.0);

        boolean plausible = true;
        double confidence = 0.85;
        if ("ZWE".equalsIgnoreCase(country) || "ZW".equalsIgnoreCase(country) || country == null) {
            if (lat < ZW_LAT_MIN || lat > ZW_LAT_MAX || lng < ZW_LNG_MIN || lng > ZW_LNG_MAX) {
                plausible = false;
                warnings.add("OUTSIDE_ZIMBABWE_BOUNDING_BOX");
                confidence = 0.30;
            }
        }
        if (accuracyMeters != null && accuracyMeters.doubleValue() > 500) {
            warnings.add("LOW_ACCURACY");
            confidence -= 0.1;
        }
        // 0.0 (null island) is almost always wrong.
        if (Math.abs(lat) < 0.0001 && Math.abs(lng) < 0.0001) {
            plausible = false;
            warnings.add("ZERO_COORDINATES_SUSPECT");
            confidence = 0.05;
        }
        Coordinate ignored = new Coordinate(lat, lng); // sanity-checks construction
        return new Result(true, plausible, errors, warnings, Math.max(0.0, Math.min(1.0, confidence)));
    }

    public record Result(boolean valid, boolean plausible, List<String> errors, List<String> warnings, double confidence) {}
}
