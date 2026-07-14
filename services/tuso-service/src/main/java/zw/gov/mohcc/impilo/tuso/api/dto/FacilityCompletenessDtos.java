package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.util.Set;

/**
 * DTOs for the governed facility profile-completion flow (facility-mode cockpit prompt).
 *
 * <p>The request deliberately accepts ONLY the completeness-governed fields (import-plane
 * acceptable-missing vocabulary: facility type / ownership / operating status / geocode)
 * plus the geo-profile address fields — never regulatory status, identity, or lifecycle
 * fields, which stay on their own governed flows.</p>
 */
public final class FacilityCompletenessDtos {

    private FacilityCompletenessDtos() {
    }

    /** How a submitted geocode was captured — mirrors NdilaLocationPicker's capture meta. */
    public static final Set<String> GEOCODE_SOURCES = Set.of("DEVICE_GPS", "ADDRESS_SEARCH", "MANUAL");

    /**
     * Provenance of a submitted geocode. Required whenever latitude/longitude are submitted.
     *
     * @param source        DEVICE_GPS | ADDRESS_SEARCH | MANUAL
     * @param provider      optional geocode provider (e.g. Ndila upstream provider) for ADDRESS_SEARCH
     * @param confidence    optional provider confidence in [0..1]
     * @param accuracyMeters optional device GPS accuracy radius in meters
     */
    public record GeocodeProvenance(
            String source,
            String provider,
            Double confidence,
            Double accuracyMeters) {
    }

    /** Governed completion payload — all fields optional; null means "leave unchanged". */
    public record CompleteFacilityProfileRequest(
            String facilityType,
            String ownership,
            String operationalStatus,
            BigDecimal latitude,
            BigDecimal longitude,
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String district,
            String ward,
            String postalCode,
            GeocodeProvenance geocodeProvenance) {
    }
}
