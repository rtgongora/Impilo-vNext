package zw.gov.mohcc.impilo.varapi.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.PublicPractitionerVerificationResponse;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerProjectionSupport;
import zw.gov.mohcc.impilo.varapi.core.PublicPractitionerVerificationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilLicenceRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilLicenceRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Public practitioner verification — allow-list guard (gateway public lane).
 *
 * <p>Every response is serialized with Jackson and asserted to contain NO
 * contact details, NO national identifier, NO date of birth and NO internal
 * identifiers. The NOT_FOUND miss must be shape-identical to a hit (no
 * existence oracle beyond {@code registerStatus}).</p>
 */
@ExtendWith(MockitoExtension.class)
class PublicPractitionerVerificationControllerTest {

    private static final String REG_NUMBER = "MDPCZ-778899";
    private static final String NATIONAL_ID = "63-123456-A-42";
    private static final String EMAIL = "t.moyo@example.org";
    private static final String PHONE = "+263771234567";
    private static final String PROVIDER_PUBLIC_ID = "ZXCVB1234567890QWERTY12345";

    @Mock private ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock private ProviderCouncilLicenceRecordRepository councilLicenceRepository;
    @Mock private LicenseRepository licenseRepository;
    @Mock private zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderGoodStandingRepository goodStandingRepository;

    private PublicPractitionerVerificationController controller;
    private ProviderEntity provider;
    private CouncilEntity council;
    private ProviderCouncilRegistrationRecordEntity registration;
    private final UUID tenantId = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        controller = new PublicPractitionerVerificationController(
                new PublicPractitionerVerificationService(
                        registrationRepository,
                        new PublicPractitionerProjectionSupport(councilLicenceRepository, licenseRepository),
                        // Rito disabled by default → experience summary is null (fail-open).
                        new zw.gov.mohcc.impilo.varapi.integration.RitoClient(
                                new org.springframework.web.client.RestTemplate(),
                                new zw.gov.mohcc.impilo.varapi.config.VarapiProperties()),
                        goodStandingRepository));
        TrustContextHolder.set(new TrustContext(
                tenantId, "public-gateway", "SYSTEM", "PUBLIC_ACCESS", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        council = new CouncilEntity();
        council.setId(5L);
        council.setCouncilCode("MDPCZ");
        council.setName("Medical and Dental Practitioners Council of Zimbabwe");

        provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(tenantId);
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

        registration = new ProviderCouncilRegistrationRecordEntity();
        registration.setTenantId(tenantId);
        registration.setProvider(provider);
        registration.setCouncil(council);
        registration.setRegistrationNumber(REG_NUMBER);
        registration.setStatus("REGISTERED");
        registration.setRegistrationDate(LocalDate.of(2019, 2, 1));
        registration.setExpiryDate(LocalDate.of(2026, 12, 31));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── hit: register facts only, never PII ─────────────────────────────────

    @Test
    void verify_registeredPractitioner_disclosesRegisterFactsOnly() throws Exception {
        ProviderCouncilLicenceRecordEntity licence = new ProviderCouncilLicenceRecordEntity();
        licence.setTenantId(tenantId);
        licence.setProvider(provider);
        licence.setCouncil(council);
        licence.setLicenceType("PRACTISING_CERTIFICATE");
        licence.setStatus("ACTIVE");
        licence.setIssueDate(LocalDate.of(2026, 1, 1));
        licence.setExpiryDate(LocalDate.of(2026, 12, 31));

        lenient().when(registrationRepository
                .findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(registration));
        lenient().when(councilLicenceRepository.findByTenantIdAndProvider_Id(tenantId, 42L))
                .thenReturn(List.of(licence));

        ApiResponse<PublicPractitionerVerificationResponse> envelope =
                controller.verify(REG_NUMBER).getBody();
        assertThat(envelope).isNotNull();
        PublicPractitionerVerificationResponse body = envelope.data();

        assertThat(body.registerStatus()).isEqualTo("REGISTERED");
        assertThat(body.displayName()).isEqualTo("Dr Tariro Moyo");
        assertThat(body.profession()).isEqualTo("Medical Practitioner");
        assertThat(body.cadre()).isEqualTo("GENERAL_PRACTITIONER");
        assertThat(body.registrationNumber()).isEqualTo(REG_NUMBER);
        assertThat(body.registeredSince()).isEqualTo(LocalDate.of(2019, 2, 1));
        assertThat(body.registrationExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(body.licenceStatus()).isEqualTo("ACTIVE");
        assertThat(body.licenceValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(body.licenceValidTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(body.registeringAuthority())
                .isEqualTo("Medical and Dental Practitioners Council of Zimbabwe");

        String serialized = json.writeValueAsString(body);
        assertThat(serialized).doesNotContain(NATIONAL_ID);
        assertThat(serialized).doesNotContain(EMAIL);
        assertThat(serialized).doesNotContain(PHONE);
        assertThat(serialized).doesNotContain("1980-03-14");
        assertThat(serialized).doesNotContain(PROVIDER_PUBLIC_ID);
    }

    // ── suspended register status is represented honestly ───────────────────

    @Test
    void verify_suspendedRegistration_isReportedHonestly() {
        registration.setStatus("SUSPENDED");
        lenient().when(registrationRepository
                .findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(registration));
        lenient().when(councilLicenceRepository.findByTenantIdAndProvider_Id(tenantId, 42L))
                .thenReturn(List.of());
        lenient().when(licenseRepository.findByProviderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of());

        PublicPractitionerVerificationResponse body =
                controller.verify(REG_NUMBER).getBody().data();

        assertThat(body.registerStatus()).isEqualTo("SUSPENDED");
        assertThat(body.licenceStatus()).isNull();
    }

    // ── legacy licence fallback when no council licence record exists ───────

    @Test
    void verify_fallsBackToLegacyLicenceWindow() {
        LicenseEntity legacy = new LicenseEntity();
        legacy.setProvider(provider);
        legacy.setTenantId(tenantId);
        legacy.setStatus("ACTIVE");
        legacy.setValidFrom(LocalDate.of(2025, 7, 1));
        legacy.setValidTo(LocalDate.of(2026, 6, 30));

        lenient().when(registrationRepository
                .findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, REG_NUMBER))
                .thenReturn(Optional.of(registration));
        lenient().when(councilLicenceRepository.findByTenantIdAndProvider_Id(tenantId, 42L))
                .thenReturn(List.of());
        lenient().when(licenseRepository.findByProviderIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(legacy));

        PublicPractitionerVerificationResponse body =
                controller.verify(REG_NUMBER).getBody().data();

        assertThat(body.licenceStatus()).isEqualTo("ACTIVE");
        assertThat(body.licenceValidFrom()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(body.licenceValidTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    // ── miss: HTTP 200, identical shape, no existence oracle ────────────────

    @Test
    void verify_unknownNumber_returns200WithUniformNotFoundShape() throws Exception {
        lenient().when(registrationRepository
                .findFirstByTenantIdAndRegistrationNumberIgnoreCase(tenantId, "NOPE-1"))
                .thenReturn(Optional.empty());

        var response = controller.verify("NOPE-1");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        PublicPractitionerVerificationResponse body = response.getBody().data();

        assertThat(body.registerStatus()).isEqualTo("NOT_FOUND");

        // Shape parity: the miss serializes with exactly the same field set as a hit.
        var missNode = json.readTree(json.writeValueAsString(body));
        var hitNode = json.readTree(json.writeValueAsString(
                new PublicPractitionerVerificationResponse("REGISTERED", "Dr X", "P", "C",
                        "N-1", LocalDate.now(), null, "ACTIVE", LocalDate.now(), null, "Council", null, "GOOD", true, "Council")));
        assertThat(iterToList(missNode.fieldNames())).isEqualTo(iterToList(hitNode.fieldNames()));
    }

    @Test
    void verify_blankNumber_isUniformNotFound() {
        PublicPractitionerVerificationResponse body = controller.verify("   ").getBody().data();
        assertThat(body.registerStatus()).isEqualTo("NOT_FOUND");
        assertThat(body.displayName()).isNull();
        assertThat(body.registeringAuthority()).isNull();
    }

    private static List<String> iterToList(java.util.Iterator<String> it) {
        List<String> out = new java.util.ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }
}
