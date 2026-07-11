package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilAffiliationRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilLicenceRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderEligibilitySnapshotRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * PIC eligibility assessment — the unified, time-specific credential verdict
 * TUSO snapshots on every nomination.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PicEligibilityAssessmentServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderCouncilRegistrationRecordRepository registrationRecordRepository;
    @Mock private ProviderCouncilAffiliationRepository councilAffiliationRepository;
    @Mock private CertificateService certificateService;
    @Mock private LicenseRepository licenseRepository;
    @Mock private DisciplinaryActionRepository disciplinaryActionRepository;
    @Mock private ProviderCouncilLicenceRecordRepository councilLicenceRecordRepository;
    @Mock private ProviderEligibilitySnapshotRepository snapshotRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private PicEligibilityAssessmentService service;
    private final UUID tenantId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private ProviderEntity provider;

    @BeforeEach
    void setUp() {
        service = new PicEligibilityAssessmentService(
                providerRepository, registrationRecordRepository, councilAffiliationRepository,
                certificateService, licenseRepository, disciplinaryActionRepository,
                councilLicenceRecordRepository, snapshotRepository, outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(
                tenantId, "tester", "OPERATOR", "OPERATIONS",
                null, UUID.randomUUID(), null, null, null,
                zw.gov.mohcc.impilo.shared.auth.AccessMode.INTERNAL));

        // Fully green fixture; individual tests degrade one axis at a time.
        CouncilEntity council = new CouncilEntity();
        council.setCouncilCode("MDPCZ");
        council.setName("Medical and Dental Practitioners Council of Zimbabwe");

        provider = new ProviderEntity();
        provider.setId(11L);
        provider.setProviderPublicId("PROVPIC0000000000000000001");
        provider.setImpiloHealthId(UUID.randomUUID());
        provider.setRegistryStatus("REGISTERED_ACTIVE");
        provider.setLifecycleStatus("LICENCED_ACTIVE");
        provider.setPrimaryCouncil(council);
        provider.setProfession("Medical Practitioner");
        provider.setCadre("Doctor");

        when(providerRepository.findByProviderPublicIdAndTenantId(anyString(), eq(tenantId)))
                .thenReturn(Optional.of(provider));

        ProviderCouncilRegistrationRecordEntity registration = new ProviderCouncilRegistrationRecordEntity();
        registration.setId(21L);
        registration.setStatus("REGISTERED");
        registration.setRegistrationNumber("MDP-12345");
        registration.setRegistrationDate(LocalDate.now().minusYears(3));
        registration.setExpiryDate(LocalDate.now().plusMonths(6));
        when(registrationRecordRepository.findByTenantIdAndProvider_Id(eq(tenantId), anyLong()))
                .thenReturn(List.of(registration));

        ProviderCertificateEntity certificate = new ProviderCertificateEntity();
        certificate.setId(31L);
        certificate.setStatus("ACTIVE");
        certificate.setCertificateNumber("PC-2026-777");
        certificate.setIssueDate(LocalDate.now().minusMonths(6));
        certificate.setExpiryDate(LocalDate.now().plusMonths(3));
        when(certificateService.getActiveCertificate(anyLong(), eq("PRACTISING_CERTIFICATE")))
                .thenReturn(Optional.of(certificate));

        LicenseEntity license = new LicenseEntity();
        license.setId(41L);
        license.setStatus("ACTIVE");
        license.setLicenseType("PRACTICE");
        license.setValidFrom(LocalDate.now().minusYears(1));
        license.setValidTo(LocalDate.now().plusMonths(4));
        when(licenseRepository.findByProviderId(anyLong())).thenReturn(List.of(license));

        when(disciplinaryActionRepository.findByProviderId(anyLong())).thenReturn(List.of());
        when(councilLicenceRecordRepository.findByTenantIdAndProvider_Id(eq(tenantId), anyLong()))
                .thenReturn(List.of());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private PicEligibilityAssessmentService.AssessmentRequest request(LocalDate asOf) {
        return new PicEligibilityAssessmentService.AssessmentRequest(
                provider.getProviderPublicId(), "6", null, "PIC_NOMINATION", asOf);
    }

    @Test
    void fullyCredentialedProviderIsEligible() {
        var response = service.assess(request(null));
        assertThat(response.result()).isEqualTo("ELIGIBLE");
        assertThat(response.eligible()).isTrue();
        assertThat(response.axes()).containsKeys(
                "identity", "council", "registration", "practisingCertificate", "licence", "restrictions");
        assertThat(response.snapshotId()).isNotNull();
    }

    @Test
    void assessmentIsTimeSpecific_certificateExpiredAtAsOf() {
        // Valid today, but assessed as of a date after the certificate's expiry.
        LocalDate afterExpiry = LocalDate.now().plusMonths(4);
        var response = service.assess(request(afterExpiry));
        assertThat(response.result()).isEqualTo("INELIGIBLE");
        assertThat(response.eligible()).isFalse();
        assertThat(String.join(" ", response.reasons())).containsIgnoringCase("expired");
    }

    @Test
    void activeDisciplinarySuspensionBlocks() {
        DisciplinaryActionEntity suspension = new DisciplinaryActionEntity();
        suspension.setId(51L);
        suspension.setActionType("SUSPENSION");
        suspension.setStatus("ACTIVE");
        suspension.setExpiryDate(LocalDate.now().plusMonths(2));
        when(disciplinaryActionRepository.findByProviderId(anyLong())).thenReturn(List.of(suspension));

        var response = service.assess(request(null));
        assertThat(response.result()).isEqualTo("INELIGIBLE");
        assertThat(response.eligible()).isFalse();
    }

    @Test
    void licenceConditionsYieldConditionalResult() {
        LicenseEntity conditioned = new LicenseEntity();
        conditioned.setId(42L);
        conditioned.setStatus("ACTIVE");
        conditioned.setLicenseType("PRACTICE");
        conditioned.setValidFrom(LocalDate.now().minusYears(1));
        conditioned.setValidTo(LocalDate.now().plusMonths(4));
        conditioned.setConditions("Supervised practice only");
        when(licenseRepository.findByProviderId(anyLong())).thenReturn(List.of(conditioned));

        var response = service.assess(request(null));
        assertThat(response.result()).isEqualTo("CONDITIONAL");
        assertThat(response.eligible()).isFalse();
        assertThat(String.join(" ", response.reasons())).containsIgnoringCase("Supervised practice");
    }

    @Test
    void unknownProviderThrows() {
        when(providerRepository.findByProviderPublicIdAndTenantId(anyString(), eq(tenantId)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assess(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider not found");
    }
}
