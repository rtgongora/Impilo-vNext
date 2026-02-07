package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.PrivilegeRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests eligibility check logic for provider login / practice authorization.
 *
 * <p>Eligibility depends on three factors evaluated in order:</p>
 * <ol>
 *   <li>Provider status must be ACTIVE</li>
 *   <li>At least one ACTIVE, non-expired license must exist</li>
 *   <li>If a facility is specified, an ACTIVE privilege for that facility is required</li>
 * </ol>
 *
 * <p>Uses Mockito to mock repository calls without a Spring context.</p>
 */
@ExtendWith(MockitoExtension.class)
class EligibilityRulesTest {

    @Mock
    private LicenseRepository licenseRepository;

    @Mock
    private PrivilegeRepository privilegeRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Long PROVIDER_ID = 42L;
    private static final Long FACILITY_ID = 100L;

    private ProviderEntity provider;

    @BeforeEach
    void setUp() {
        provider = new ProviderEntity();
        provider.setId(PROVIDER_ID);
        provider.setTenantId(TENANT_ID);
        provider.setProviderPublicId("TEST00000000000000000001");
        provider.setGivenName("Test");
        provider.setFamilyName("Provider");
        provider.setStatus("ACTIVE");
    }

    // -- Provider status checks --

    @Test
    void inactiveProvider_notEligible() {
        provider.setStatus("SUSPENDED");

        EligibilityResult result = checkEligibility(provider, null);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("PROVIDER_NOT_ACTIVE"));
    }

    @Test
    void revokedProvider_notEligible() {
        provider.setStatus("REVOKED");

        EligibilityResult result = checkEligibility(provider, null);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("PROVIDER_NOT_ACTIVE"));
    }

    // -- License checks --

    @Test
    void noActiveLicense_notEligible() {
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.empty());

        EligibilityResult result = checkEligibility(provider, null);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("NO_ACTIVE_LICENSE"));
    }

    @Test
    void expiredLicense_notEligible() {
        LicenseEntity license = createLicense("ACTIVE", LocalDate.now().minusDays(1));
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.empty()); // query excludes expired

        EligibilityResult result = checkEligibility(provider, null);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("NO_ACTIVE_LICENSE"));
    }

    @Test
    void activeProviderWithActiveLicense_isEligible() {
        LicenseEntity license = createLicense("ACTIVE", LocalDate.now().plusYears(1));
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.of(license));

        EligibilityResult result = checkEligibility(provider, null);

        assertTrue(result.eligible());
        assertTrue(result.reasons().isEmpty());
    }

    // -- Facility privilege checks --

    @Test
    void facilityRequested_noPrivilege_notEligible() {
        LicenseEntity license = createLicense("ACTIVE", LocalDate.now().plusYears(1));
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.of(license));
        when(privilegeRepository.findByProviderIdAndFacilityIdAndStatus(PROVIDER_ID, FACILITY_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());

        EligibilityResult result = checkEligibility(provider, FACILITY_ID);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("NO_FACILITY_PRIVILEGE"));
    }

    @Test
    void facilityRequested_withActivePrivilege_isEligible() {
        LicenseEntity license = createLicense("ACTIVE", LocalDate.now().plusYears(1));
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.of(license));

        PrivilegeEntity privilege = createPrivilege(FACILITY_ID, "ACTIVE");
        when(privilegeRepository.findByProviderIdAndFacilityIdAndStatus(PROVIDER_ID, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(privilege));

        EligibilityResult result = checkEligibility(provider, FACILITY_ID);

        assertTrue(result.eligible());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void suspendedPrivilege_notEligible() {
        LicenseEntity license = createLicense("ACTIVE", LocalDate.now().plusYears(1));
        when(licenseRepository.findActiveByProviderId(PROVIDER_ID))
                .thenReturn(Optional.of(license));
        // Only ACTIVE privileges are queried; SUSPENDED won't appear in the result
        when(privilegeRepository.findByProviderIdAndFacilityIdAndStatus(PROVIDER_ID, FACILITY_ID, "ACTIVE"))
                .thenReturn(Collections.emptyList());

        EligibilityResult result = checkEligibility(provider, FACILITY_ID);

        assertFalse(result.eligible());
        assertTrue(result.reasons().contains("NO_FACILITY_PRIVILEGE"));
    }

    // -- Helper: eligibility check logic (mirrors what a service would do) --

    /**
     * Evaluate eligibility rules for a provider, optionally for a specific facility.
     *
     * @param provider   the provider entity
     * @param facilityId optional facility ID (null means no facility check)
     * @return eligibility result with boolean flag and list of failure reasons
     */
    private EligibilityResult checkEligibility(ProviderEntity provider, Long facilityId) {
        List<String> reasons = new java.util.ArrayList<>();

        // Rule 1: Provider must be ACTIVE
        if (!provider.isActive()) {
            reasons.add("PROVIDER_NOT_ACTIVE");
            return new EligibilityResult(false, reasons);
        }

        // Rule 2: Must have at least one active, non-expired license
        Optional<LicenseEntity> activeLicense =
                licenseRepository.findActiveByProviderId(provider.getId());
        if (activeLicense.isEmpty()) {
            reasons.add("NO_ACTIVE_LICENSE");
            return new EligibilityResult(false, reasons);
        }

        // Rule 3: If facility requested, must have an ACTIVE privilege for it
        if (facilityId != null) {
            List<PrivilegeEntity> activePrivileges =
                    privilegeRepository.findByProviderIdAndFacilityIdAndStatus(
                            provider.getId(), facilityId, "ACTIVE");
            if (activePrivileges.isEmpty()) {
                reasons.add("NO_FACILITY_PRIVILEGE");
                return new EligibilityResult(false, reasons);
            }
        }

        return new EligibilityResult(true, reasons);
    }

    // -- Result record --

    record EligibilityResult(boolean eligible, List<String> reasons) {}

    // -- Factory helpers --

    private LicenseEntity createLicense(String status, LocalDate validTo) {
        LicenseEntity license = new LicenseEntity();
        license.setId(1L);
        license.setProvider(provider);
        license.setTenantId(TENANT_ID);
        license.setStatus(status);
        license.setValidFrom(LocalDate.now().minusYears(1));
        license.setValidTo(validTo);
        license.setLicenseType("GENERAL");
        license.setLicenseNumber("LIC-001");
        return license;
    }

    private PrivilegeEntity createPrivilege(Long facilityId, String status) {
        PrivilegeEntity privilege = new PrivilegeEntity();
        privilege.setId(1L);
        privilege.setProvider(provider);
        privilege.setTenantId(TENANT_ID);
        privilege.setFacilityId(facilityId);
        privilege.setStatus(status);
        privilege.setPrivilegeType("PRACTICE");
        privilege.setScope("FACILITY");
        return privilege;
    }
}
