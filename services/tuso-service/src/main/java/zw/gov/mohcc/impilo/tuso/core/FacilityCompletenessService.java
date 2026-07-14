package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Governed facility-profile completeness read model.
 *
 * <p>Computed on read from {@link FacilityEntity} + {@link FacilityGeoEntity} — there is
 * deliberately no completeness table: the registry rows themselves are the truth and the
 * missing-field vocabulary stays aligned with the import plane
 * ({@code FacilityMasterImportService.acceptableMissingFlags()}: latitude / longitude /
 * facility type / ownership / operating status) plus the geo-profile address basics
 * (address line 1, city, ward) that facility managers are prompted to complete.</p>
 *
 * <p>Consumed by the facility-mode cockpit (via the experience-bff) to prompt the
 * facility administrator / practitioner-in-charge to complete missing governed fields,
 * especially geocodes.</p>
 */
@Service
public class FacilityCompletenessService {

    private static final Logger log = LoggerFactory.getLogger(FacilityCompletenessService.class);

    /** Missing-field keys — aligned with the import plane's acceptable-missing vocabulary. */
    public static final String MISSING_LATITUDE = "missing_latitude";
    public static final String MISSING_LONGITUDE = "missing_longitude";
    public static final String MISSING_FACILITY_TYPE = "missing_facility_type";
    public static final String MISSING_OWNERSHIP = "missing_ownership";
    public static final String MISSING_OPERATING_STATUS = "missing_operating_status";
    public static final String MISSING_ADDRESS_LINE1 = "missing_address_line1";
    public static final String MISSING_CITY = "missing_city";
    public static final String MISSING_WARD = "missing_ward";

    public static final String GEOCODE_PRESENT = "PRESENT";
    public static final String GEOCODE_MISSING = "MISSING";

    /** Completeness verdict for a facility profile. */
    public record FacilityCompleteness(
            Long facilityId,
            UUID facilityUuid,
            boolean complete,
            List<String> missingFields,
            String geocodeStatus) {
    }

    private final FacilityRepository facilityRepository;
    private final FacilityGeoRepository geoRepository;

    public FacilityCompletenessService(FacilityRepository facilityRepository,
                                       FacilityGeoRepository geoRepository) {
        this.facilityRepository = facilityRepository;
        this.geoRepository = geoRepository;
    }

    /**
     * Compute the governed completeness of a facility profile.
     *
     * <p>Tenant-isolated read (same guard as {@code FacilityService.getFacility}).</p>
     *
     * @param facilityId the numeric TUSO facility id
     * @return the completeness verdict: overall flag, the specific missing-field keys,
     *         and a geocode status ({@code PRESENT} / {@code MISSING})
     */
    @Transactional(readOnly = true)
    public FacilityCompleteness computeCompleteness(Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (!facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }
        FacilityGeoEntity geo = geoRepository.findByFacilityId(facilityId).orElse(null);
        return evaluate(facility, geo);
    }

    /** Pure evaluation — shared by the read endpoint and the completion flow's re-read. */
    static FacilityCompleteness evaluate(FacilityEntity facility, FacilityGeoEntity geo) {
        List<String> missing = new ArrayList<>();
        if (facility.getLatitude() == null) missing.add(MISSING_LATITUDE);
        if (facility.getLongitude() == null) missing.add(MISSING_LONGITUDE);
        if (isBlank(facility.getFacilityType())) missing.add(MISSING_FACILITY_TYPE);
        if (isBlank(facility.getOwnership())) missing.add(MISSING_OWNERSHIP);
        if (isBlank(facility.getOperationalStatus())) missing.add(MISSING_OPERATING_STATUS);
        if (geo == null || isBlank(geo.getAddressLine1())) missing.add(MISSING_ADDRESS_LINE1);
        if (geo == null || isBlank(geo.getCity())) missing.add(MISSING_CITY);
        if (geo == null || isBlank(geo.getWard())) missing.add(MISSING_WARD);

        String geocodeStatus = (facility.getLatitude() == null || facility.getLongitude() == null)
                ? GEOCODE_MISSING : GEOCODE_PRESENT;

        return new FacilityCompleteness(
                facility.getId(), facility.getFacilityUuid(), missing.isEmpty(),
                List.copyOf(missing), geocodeStatus);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
