package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityCompletenessServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilityGeoRepository geoRepository;

    private FacilityCompletenessService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID facilityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        service = new FacilityCompletenessService(facilityRepository, geoRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "actor-1", "PROVIDER", "FACILITY_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private FacilityEntity facility() {
        FacilityEntity f = new FacilityEntity();
        f.setId(42L);
        f.setFacilityUuid(facilityUuid);
        f.setTenantId(tenantId);
        f.setFacilityCode("ZW010125");
        f.setName("Bangure Clinic");
        return f;
    }

    private static FacilityGeoEntity fullGeo(FacilityEntity f) {
        FacilityGeoEntity geo = new FacilityGeoEntity();
        geo.setFacility(f);
        geo.setAddressLine1("12 Chaminuka Rd");
        geo.setCity("Harare");
        geo.setWard("Ward 7");
        return geo;
    }

    private static void makeCoreComplete(FacilityEntity f) {
        f.setLatitude(new BigDecimal("-17.8292000"));
        f.setLongitude(new BigDecimal("31.0522000"));
        f.setFacilityType("CLINIC");
        f.setOwnership("GOVERNMENT");
        f.setOperationalStatus("OPERATIONAL");
    }

    // ── field matrix ────────────────────────────────────────────────────────

    @Test
    void fullyPopulatedProfileIsComplete() {
        FacilityEntity f = facility();
        makeCoreComplete(f);
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.of(fullGeo(f)));

        var result = service.computeCompleteness(42L);

        assertThat(result.complete()).isTrue();
        assertThat(result.missingFields()).isEmpty();
        assertThat(result.geocodeStatus()).isEqualTo(FacilityCompletenessService.GEOCODE_PRESENT);
        assertThat(result.facilityUuid()).isEqualTo(facilityUuid);
    }

    @Test
    void bareFacilityWithoutGeoRowReportsEveryMissingField() {
        FacilityEntity f = facility();
        f.setOperationalStatus(null); // entity default is OPERATIONAL; null it for the matrix
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.empty());

        var result = service.computeCompleteness(42L);

        assertThat(result.complete()).isFalse();
        assertThat(result.missingFields()).containsExactlyInAnyOrder(
                FacilityCompletenessService.MISSING_LATITUDE,
                FacilityCompletenessService.MISSING_LONGITUDE,
                FacilityCompletenessService.MISSING_FACILITY_TYPE,
                FacilityCompletenessService.MISSING_OWNERSHIP,
                FacilityCompletenessService.MISSING_OPERATING_STATUS,
                FacilityCompletenessService.MISSING_ADDRESS_LINE1,
                FacilityCompletenessService.MISSING_CITY,
                FacilityCompletenessService.MISSING_WARD);
        assertThat(result.geocodeStatus()).isEqualTo(FacilityCompletenessService.GEOCODE_MISSING);
    }

    @Test
    void missingLatitudeOnlyFlagsGeocodeMissing() {
        FacilityEntity f = facility();
        makeCoreComplete(f);
        f.setLatitude(null);
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.of(fullGeo(f)));

        var result = service.computeCompleteness(42L);

        assertThat(result.complete()).isFalse();
        assertThat(result.missingFields()).containsExactly(FacilityCompletenessService.MISSING_LATITUDE);
        assertThat(result.geocodeStatus()).isEqualTo(FacilityCompletenessService.GEOCODE_MISSING);
    }

    @Test
    void blankStringsCountAsMissing() {
        FacilityEntity f = facility();
        makeCoreComplete(f);
        f.setFacilityType("  ");
        f.setOwnership("");
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));
        FacilityGeoEntity geo = fullGeo(f);
        geo.setWard("   ");
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.of(geo));

        var result = service.computeCompleteness(42L);

        assertThat(result.missingFields()).containsExactlyInAnyOrder(
                FacilityCompletenessService.MISSING_FACILITY_TYPE,
                FacilityCompletenessService.MISSING_OWNERSHIP,
                FacilityCompletenessService.MISSING_WARD);
        assertThat(result.geocodeStatus()).isEqualTo(FacilityCompletenessService.GEOCODE_PRESENT);
    }

    @Test
    void geoRowPresentButAddressBasicsBlankStillMissing() {
        FacilityEntity f = facility();
        makeCoreComplete(f);
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));
        FacilityGeoEntity geo = new FacilityGeoEntity();
        geo.setFacility(f);
        when(geoRepository.findByFacilityId(42L)).thenReturn(Optional.of(geo));

        var result = service.computeCompleteness(42L);

        assertThat(result.missingFields()).containsExactlyInAnyOrder(
                FacilityCompletenessService.MISSING_ADDRESS_LINE1,
                FacilityCompletenessService.MISSING_CITY,
                FacilityCompletenessService.MISSING_WARD);
    }

    // ── guards ──────────────────────────────────────────────────────────────

    @Test
    void crossTenantReadIsRejected() {
        FacilityEntity f = facility();
        f.setTenantId(UUID.randomUUID());
        when(facilityRepository.findById(42L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.computeCompleteness(42L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant isolation");
    }

    @Test
    void unknownFacilityIsRejected() {
        when(facilityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.computeCompleteness(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Facility not found");
    }
}
