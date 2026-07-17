package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicFacilityPractitionerResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerProjectionSupport.LicenceWindow;
import zw.gov.mohcc.impilo.varapi.enums.PracticeContextType;
import zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderPracticeContextRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Facility-scoped verified-practitioner listing — 4-axis truth gate + allow-list guard.
 *
 * <p>Proves: (a) a provider missing ANY one of the four axes (approved+in-window
 * affiliation, operational+verified registry standing, sufficient platform trust,
 * active FACILITY practice context) is silently dropped; (b) a fully-confirmed
 * provider is projected with the allow-listed fields and NO PII; (c) an unknown
 * facility yields an empty list (no existence oracle).</p>
 */
@ExtendWith(MockitoExtension.class)
class PublicFacilityPractitionerServiceTest {

    private static final long FACILITY_ID = 100L;
    private static final long PROVIDER_ID = 42L;

    private static final String NATIONAL_ID = "63-123456-A-42";
    private static final String EMAIL = "t.moyo@example.org";
    private static final String PHONE = "+263771234567";
    private static final String PROVIDER_PUBLIC_ID = "ZXCVB1234567890QWERTY12345";

    @Mock private ProviderAffiliationRepository affiliationRepository;
    @Mock private ProviderPracticeContextRepository practiceContextRepository;
    @Mock private ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock private PublicPractitionerProjectionSupport projection;

    private PublicFacilityPractitionerService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private CouncilEntity council;

    @BeforeEach
    void setUp() {
        service = new PublicFacilityPractitionerService(
                affiliationRepository, practiceContextRepository, registrationRepository, projection);

        council = new CouncilEntity();
        council.setId(5L);
        council.setCouncilCode("MDPCZ");
        council.setName("Medical and Dental Practitioners Council of Zimbabwe");
    }

    // ── (b) fully-confirmed provider is projected, allow-listed, NO PII ──────

    @Test
    void listsFullyConfirmedPractitioner_withAllowListedFieldsOnly() throws Exception {
        ProviderEntity provider = confirmedProvider();
        stubActiveFacilityContext(provider);
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant Physician")));

        ProviderCouncilRegistrationRecordEntity registration = new ProviderCouncilRegistrationRecordEntity();
        registration.setProvider(provider);
        registration.setCouncil(council);
        registration.setRegistrationNumber("MDPCZ-778899");
        registration.setStatus("REGISTERED");
        registration.setRegistrationDate(LocalDate.of(2019, 2, 1));
        registration.setExpiryDate(LocalDate.of(2026, 12, 31));
        when(registrationRepository.findByTenantIdAndProvider_Id(tenantId, PROVIDER_ID))
                .thenReturn(List.of(registration));

        when(projection.resolveLicenceWindow(tenantId, PROVIDER_ID))
                .thenReturn(new LicenceWindow("ACTIVE", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        List<PublicFacilityPractitionerResponse> result =
                service.listVerifiedPractitioners(tenantId, FACILITY_ID);

        assertThat(result).hasSize(1);
        PublicFacilityPractitionerResponse row = result.get(0);
        assertThat(row.displayName()).isEqualTo("Dr Tariro Moyo");
        assertThat(row.profession()).isEqualTo("Medical Practitioner");
        assertThat(row.cadre()).isEqualTo("GENERAL_PRACTITIONER");
        assertThat(row.roleTitle()).isEqualTo("Consultant Physician");
        assertThat(row.registrationNumber()).isEqualTo("MDPCZ-778899");
        assertThat(row.registerStatus()).isEqualTo("REGISTERED");
        assertThat(row.registeredSince()).isEqualTo(LocalDate.of(2019, 2, 1));
        assertThat(row.registrationExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(row.licenceStatus()).isEqualTo("ACTIVE");
        assertThat(row.licenceValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(row.licenceValidTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(row.registeringAuthority())
                .isEqualTo("Medical and Dental Practitioners Council of Zimbabwe");

        String serialized = json.writeValueAsString(row);
        assertThat(serialized).doesNotContain(NATIONAL_ID);
        assertThat(serialized).doesNotContain(EMAIL);
        assertThat(serialized).doesNotContain(PHONE);
        assertThat(serialized).doesNotContain("1980-03-14");
        assertThat(serialized).doesNotContain(PROVIDER_PUBLIC_ID);
        assertThat(serialized).doesNotContain(healthId.toString());
        assertThat(serialized).doesNotContain(tenantId.toString());
    }

    /** Role title falls back to a human label from affiliation_type when none is set. */
    @Test
    void roleTitleFallsBackToHumanisedAffiliationType() {
        ProviderEntity provider = confirmedProvider();
        stubActiveFacilityContext(provider);
        ProviderAffiliationEntity aff = approvedAffiliation(provider, null);
        aff.setAffiliationType("VISITING_CONSULTANT");
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(aff));
        when(registrationRepository.findByTenantIdAndProvider_Id(tenantId, PROVIDER_ID)).thenReturn(List.of());
        when(projection.resolveLicenceWindow(tenantId, PROVIDER_ID)).thenReturn(LicenceWindow.EMPTY);

        List<PublicFacilityPractitionerResponse> result =
                service.listVerifiedPractitioners(tenantId, FACILITY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).roleTitle()).isEqualTo("Visiting Consultant");
    }

    // ── (a) missing ANY one axis drops the provider ─────────────────────────

    @Test
    void excludesWhenAffiliationNotApproved() {
        ProviderEntity provider = confirmedProvider();
        stubActiveFacilityContext(provider);
        ProviderAffiliationEntity aff = approvedAffiliation(provider, "Consultant");
        aff.setApprovalState("PENDING");
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(aff));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenAffiliationWindowHasClosed() {
        ProviderEntity provider = confirmedProvider();
        stubActiveFacilityContext(provider);
        ProviderAffiliationEntity aff = approvedAffiliation(provider, "Consultant");
        aff.setEndDate(LocalDate.now().minusDays(1));
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(aff));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenLifecycleNotOperational() {
        ProviderEntity provider = confirmedProvider();
        provider.setLifecycleStatus(ProviderLifecycleStatus.SUSPENDED.name());
        stubActiveFacilityContext(provider);
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant")));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenRegistryStatusNotVerified() {
        ProviderEntity provider = confirmedProvider();
        provider.setRegistryStatus(ProviderRegistryStatus.PENDING_VERIFICATION.name());
        stubActiveFacilityContext(provider);
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant")));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenTrustBelowCouncilVerified() {
        ProviderEntity provider = confirmedProvider();
        provider.setTrustLevel(ProviderTrustLevel.DOCUMENT_SUPPORTED.name());
        stubActiveFacilityContext(provider);
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant")));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenNoActiveFacilityPracticeContext() {
        ProviderEntity provider = confirmedProvider();
        // No practice-context row for this provider at this facility.
        when(practiceContextRepository.findByTenantIdAndLinkedFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of());
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant")));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    @Test
    void excludesWhenPracticeContextIsNotFacilityType() {
        ProviderEntity provider = confirmedProvider();
        ProviderPracticeContextEntity ctx = new ProviderPracticeContextEntity();
        ctx.setProvider(provider);
        ctx.setStatus("ACTIVE");
        ctx.setContextType(PracticeContextType.VIRTUAL.name()); // telemedicine only, not a facility axis
        ctx.setLinkedFacilityId(FACILITY_ID);
        when(practiceContextRepository.findByTenantIdAndLinkedFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(ctx));
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(approvedAffiliation(provider, "Consultant")));

        assertThat(service.listVerifiedPractitioners(tenantId, FACILITY_ID)).isEmpty();
    }

    // ── (c) unknown facility → empty list (no existence oracle) ─────────────

    @Test
    void unknownFacilityReturnsEmptyList() {
        when(practiceContextRepository.findByTenantIdAndLinkedFacilityIdAndStatus(tenantId, 999L, "ACTIVE"))
                .thenReturn(List.of());
        when(affiliationRepository.findByTenantIdAndFacilityIdAndStatus(tenantId, 999L, "ACTIVE"))
                .thenReturn(List.of());

        assertThat(service.listVerifiedPractitioners(tenantId, 999L)).isEmpty();
    }

    @Test
    void nullFacilityOrTenantReturnsEmptyList() {
        assertThat(service.listVerifiedPractitioners(tenantId, null)).isEmpty();
        assertThat(service.listVerifiedPractitioners(null, FACILITY_ID)).isEmpty();
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private ProviderEntity confirmedProvider() {
        ProviderEntity provider = new ProviderEntity();
        provider.setId(PROVIDER_ID);
        provider.setTenantId(tenantId);
        provider.setImpiloHealthId(healthId);
        provider.setProviderPublicId(PROVIDER_PUBLIC_ID);
        provider.setTitle("Dr");
        provider.setGivenName("Tariro");
        provider.setFamilyName("Moyo");
        provider.setProfession("Medical Practitioner");
        provider.setCadre("GENERAL_PRACTITIONER");
        provider.setNationalId(NATIONAL_ID);
        provider.setEmail(EMAIL);
        provider.setPhone(PHONE);
        provider.setDateOfBirth(LocalDate.of(1980, 3, 14));
        provider.setLifecycleStatus(ProviderLifecycleStatus.LICENCED_ACTIVE.name());
        provider.setRegistryStatus(ProviderRegistryStatus.REGISTERED_ACTIVE.name());
        provider.setTrustLevel(ProviderTrustLevel.COUNCIL_VERIFIED.name());
        return provider;
    }

    private ProviderAffiliationEntity approvedAffiliation(ProviderEntity provider, String roleTitle) {
        ProviderAffiliationEntity aff = new ProviderAffiliationEntity();
        aff.setProvider(provider);
        aff.setTenantId(tenantId);
        aff.setFacilityId(FACILITY_ID);
        aff.setAffiliationType("EMPLOYMENT");
        aff.setRoleTitle(roleTitle);
        aff.setStatus("ACTIVE");
        aff.setApprovalState("APPROVED");
        return aff;
    }

    private void stubActiveFacilityContext(ProviderEntity provider) {
        ProviderPracticeContextEntity ctx = new ProviderPracticeContextEntity();
        ctx.setProvider(provider);
        ctx.setTenantId(tenantId);
        ctx.setStatus("ACTIVE");
        ctx.setContextType(PracticeContextType.FACILITY.name());
        ctx.setLinkedFacilityId(FACILITY_ID);
        lenient().when(practiceContextRepository
                        .findByTenantIdAndLinkedFacilityIdAndStatus(tenantId, FACILITY_ID, "ACTIVE"))
                .thenReturn(List.of(ctx));
    }
}
