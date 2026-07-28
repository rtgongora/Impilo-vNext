package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.integration.MinistryAuthorityClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * FCV-W2 — who may confer Ministry legitimacy on a facility, and over which facilities.
 *
 * <p>Before this, the verification lane decided authority from a caller-supplied
 * {@code X-Actor-Type} header. These cases pin the replacement: authority is an ACTIVE appointment
 * at the Ministry organisation whose jurisdiction covers the facility.</p>
 */
@ExtendWith(MockitoExtension.class)
class MinistryAuthorityServiceTest {

    @Mock private MinistryAuthorityClient client;

    private MinistryAuthorityService service() {
        return new MinistryAuthorityService(client);
    }

    private FacilityEntity facility(String province, String district) {
        FacilityEntity f = new FacilityEntity();
        f.setFacilityUuid(UUID.randomUUID());
        f.setProvince(province);
        f.setDistrict(district);
        return f;
    }

    private Map<String, Object> appointment(String role, String jurisdiction, String status, String org) {
        return Map.of("roleCode", role, "jurisdictionCode", jurisdiction,
                "status", status, "organizationId", org);
    }

    private static final String MOHCC = MinistryAuthorityService.MOHCC_ORGANISATION_ID;

    // ── the jurisdiction grammar ────────────────────────────────────────────────────────────

    @Test
    void nationalCoversAnyFacility() {
        assertThat(MinistryAuthorityService.covers("NATIONAL", facility("Manicaland", "Buhera"))).isTrue();
        // org-registry defaults a missing jurisdiction to NATIONAL
        assertThat(MinistryAuthorityService.covers(null, facility("Manicaland", "Buhera"))).isTrue();
    }

    @Test
    void provinceCoversItsOwnFacilitiesOnly() {
        assertThat(MinistryAuthorityService.covers("PROVINCE:MANICALAND", facility("Manicaland", "Buhera"))).isTrue();
        assertThat(MinistryAuthorityService.covers("PROVINCE:MANICALAND", facility("Midlands", "Gweru"))).isFalse();
    }

    @Test
    void districtCoversItsOwnDistrictOnly_andNeverWidens() {
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", facility("Manicaland", "Buhera"))).isTrue();
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", facility("Manicaland", "Mutare"))).isFalse();
        // A district officer does not inherit the province: narrower never satisfies wider.
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", facility("Manicaland", null))).isFalse();
    }

    @Test
    void aFacilityWithNoDistrictIsOutsideEveryDistrictJurisdiction() {
        // 5,510 of the estate's facilities (the regulator-listed set) carry a province but no
        // district. Treating "unknown district" as "my district" would let one officer decide for
        // three quarters of the country, so absence must refuse — while the province-scoped and
        // national verifiers above still reach them.
        FacilityEntity noDistrict = facility("Manicaland", null);
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", noDistrict)).isFalse();
        assertThat(MinistryAuthorityService.covers("DISTRICT:", noDistrict)).isFalse();
        assertThat(MinistryAuthorityService.covers("PROVINCE:MANICALAND", noDistrict)).isTrue();
        assertThat(MinistryAuthorityService.covers("NATIONAL", noDistrict)).isTrue();

        FacilityEntity blankDistrict = facility("Manicaland", "   ");
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", blankDistrict)).isFalse();
    }

    @Test
    void placeNamesCompareRobustlyWithoutACatalogue() {
        // The live estate's only variance is case ("Chipinge" vs "chipinge"), but these columns are
        // free text with no catalogue, so the comparison must not break the first time someone types
        // a suffix or a double space.
        assertThat(MinistryAuthorityService.covers("DISTRICT:CHIPINGE", facility("Manicaland", "chipinge"))).isTrue();
        assertThat(MinistryAuthorityService.covers("DISTRICT:GOKWE SOUTH", facility("Midlands", "Gokwe  south"))).isTrue();
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", facility("Manicaland", "Buhera District"))).isTrue();
        assertThat(MinistryAuthorityService.covers("PROVINCE:MIDLANDS", facility("Midlands Province", "Gweru"))).isTrue();
        // Still not a fuzzy match — a different place is a different place.
        assertThat(MinistryAuthorityService.covers("DISTRICT:BUHERA", facility("Manicaland", "Buherra"))).isFalse();
    }

    @Test
    void anUnrecognisedJurisdictionGrantsNothing() {
        // Not a licence to act everywhere — an unparseable code is a refusal, not a wildcard.
        assertThat(MinistryAuthorityService.covers("REGION:EAST", facility("Manicaland", "Buhera"))).isFalse();
    }

    // ── resolving authority from appointments ───────────────────────────────────────────────

    @Test
    void anActiveMohccVerifierWhoseJurisdictionCoversTheFacilityHasAuthority() {
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of(
                appointment("MOHCC_DISTRICT_VERIFIER", "DISTRICT:BUHERA", "ACTIVE", MOHCC)));

        assertThat(service().authorityOver("HID-1", facility("Manicaland", "Buhera"),
                MinistryAuthorityService.VERIFIER_ROLES))
                .isPresent()
                .get().extracting(MinistryAuthorityService.Authority::roleCode)
                .isEqualTo("MOHCC_DISTRICT_VERIFIER");
    }

    @Test
    void aVerifierHasNoAuthorityOutsideTheirDistrict() {
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of(
                appointment("MOHCC_DISTRICT_VERIFIER", "DISTRICT:BUHERA", "ACTIVE", MOHCC)));

        assertThat(service().authorityOver("HID-1", facility("Midlands", "Gweru"),
                MinistryAuthorityService.VERIFIER_ROLES)).isEmpty();
    }

    @Test
    void anEndedOrPendingAppointmentIsNotAuthority() {
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of(
                appointment("MOHCC_NATIONAL_VERIFIER", "NATIONAL", "ENDED", MOHCC),
                appointment("MOHCC_NATIONAL_VERIFIER", "NATIONAL", "PENDING_VERIFICATION", MOHCC)));

        assertThat(service().authorityOver("HID-1", facility("Harare", "Harare"),
                MinistryAuthorityService.VERIFIER_ROLES)).isEmpty();
    }

    @Test
    void authorityAtAProfessionalCouncilIsNotAuthorityOverFacilities() {
        // A nurses-council registrar is a real appointment — just not one about facilities.
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of(
                appointment("MOHCC_NATIONAL_VERIFIER", "NATIONAL", "ACTIVE",
                        "a5000000-0000-4000-8000-000000000003")));

        assertThat(service().authorityOver("HID-1", facility("Harare", "Harare"),
                MinistryAuthorityService.VERIFIER_ROLES)).isEmpty();
    }

    @Test
    void administeringTheRegistryIsNotVerifyingAFacility() {
        // Separation of duties: running the registry and deciding a facility is legitimate are
        // different acts, so the administrator role is absent from VERIFIER_ROLES.
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of(
                appointment("MOHCC_REGISTRY_ADMINISTRATOR", "NATIONAL", "ACTIVE", MOHCC)));

        assertThat(service().authorityOver("HID-1", facility("Harare", "Harare"),
                MinistryAuthorityService.VERIFIER_ROLES)).isEmpty();
        assertThat(service().authorityOver("HID-1", facility("Harare", "Harare"),
                MinistryAuthorityService.REGISTRY_ADMIN_ROLES)).isPresent();
    }

    @Test
    void anUnreachableRegistryDeniesRatherThanGrants() {
        // The client returns empty on outage; the act must refuse, never fall open.
        when(client.appointmentsForPerson("HID-1")).thenReturn(List.of());

        assertThat(service().authorityOver("HID-1", facility("Harare", "Harare"),
                MinistryAuthorityService.VERIFIER_ROLES)).isEmpty();
    }
}
