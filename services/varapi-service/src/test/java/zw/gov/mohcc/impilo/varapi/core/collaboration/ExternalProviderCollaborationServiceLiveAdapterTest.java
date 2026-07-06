package zw.gov.mohcc.impilo.varapi.core.collaboration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.CreateExternalProviderProfileRequest;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilAdapterCallAuditEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ExternalProviderAccessProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ExternalProviderVerificationAttemptEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilAdapterCallAuditRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ExternalProviderAccessProfileRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ExternalProviderVerificationAttemptRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Channel A live council adapter seam wiring in {@link ExternalProviderCollaborationService}.
 * Proves: (1) adapter OFF ⇒ existing local imported-registry path unchanged; (2) adapter ON +
 * mock-authority match ⇒ a COUNCIL_LIVE attempt with the AUTHORITY's own confidence, local
 * path bypassed; (3) adapter ON + unreachable ⇒ honest degraded, no PASS, falls through and
 * respects policy-deny-when-unreachable.
 */
@ExtendWith(MockitoExtension.class)
class ExternalProviderCollaborationServiceLiveAdapterTest {

    @Mock ExternalProviderAccessProfileRepository profileRepository;
    @Mock ExternalProviderVerificationAttemptRepository verificationRepository;
    @Mock CouncilRepository councilRepository;
    @Mock ProviderCouncilRegistrationRecordRepository councilRegistrationRecordRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock ObjectProvider<CouncilRegistryAdapter> liveAdapterProvider;
    @Mock CouncilAdapterCallAuditRepository adapterCallAuditRepository;
    @Mock CouncilRegistryAdapter liveAdapter;

    private final VarapiProperties properties = new VarapiProperties();

    private ExternalProviderCollaborationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final Long councilId = 7L;

    @BeforeEach
    void setUp() {
        service = new ExternalProviderCollaborationService(
                profileRepository, verificationRepository, councilRepository,
                councilRegistrationRecordRepository, outboxRepository,
                liveAdapterProvider, adapterCallAuditRepository, properties);

        CouncilEntity council = council();
        when(councilRepository.findByIdAndTenantId(councilId, tenantId)).thenReturn(Optional.of(council));
        when(profileRepository.save(any(ExternalProviderAccessProfileEntity.class)))
                .thenAnswer(inv -> {
                    ExternalProviderAccessProfileEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(1L); // emulate DB-assigned identity
                    }
                    return e;
                });
    }

    private CouncilEntity council() {
        CouncilEntity c = new CouncilEntity();
        c.setId(councilId);
        c.setCouncilCode("MDPCZ");
        c.setName("Medical & Dental Practitioners Council of Zimbabwe");
        return c;
    }

    private CreateExternalProviderProfileRequest request() {
        return new CreateExternalProviderProfileRequest(
                tenantId, "GRANT-1", councilId, "EC-12345",
                "Tariro", "Moyo", "Doctor", "t@example.org", "+263", "Clinic");
    }

    private ProviderCouncilRegistrationRecordEntity localHit() {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("PUB-LOCAL-1");
        provider.setGivenName("Tariro");
        provider.setFamilyName("Moyo");
        ProviderCouncilRegistrationRecordEntity rec = new ProviderCouncilRegistrationRecordEntity();
        rec.setProvider(provider);
        rec.setRegistrationNumber("EC-12345");
        return rec;
    }

    // (1) Adapter OFF: the local imported-registry path runs unchanged.
    @Test
    void adapterDisabled_usesLocalImportedRegistryPathUnchanged() {
        when(liveAdapterProvider.getIfAvailable()).thenReturn(null);
        when(councilRegistrationRecordRepository
                .findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenantId, councilId, "EC-12345"))
                .thenReturn(Optional.of(localHit()));

        var resp = service.createProfile(request());

        // Identity aligned against the LOCAL registry (unchanged semantics).
        assertThat(resp.trustLevel()).isEqualTo(ExternalProviderCollaborationService.TRUST_COUNCIL_IDENTITY);
        assertThat(resp.councilRegistrationMatched()).isTrue();
        assertThat(resp.matchedProviderPublicId()).isEqualTo("PUB-LOCAL-1");

        // No live adapter interaction, no live audit row.
        verifyNoInteractions(liveAdapter);
        verify(adapterCallAuditRepository, never()).save(any());

        // The single verification attempt is the local IMPORTED_REGISTRY one.
        ArgumentCaptor<ExternalProviderVerificationAttemptEntity> cap =
                ArgumentCaptor.forClass(ExternalProviderVerificationAttemptEntity.class);
        verify(verificationRepository).save(cap.capture());
        assertThat(cap.getValue().getSourceType()).isEqualTo("IMPORTED_REGISTRY");
    }

    // (2) Adapter ON + mock-authority match: COUNCIL_LIVE attempt with the authority's confidence.
    @Test
    void adapterEnabledAndMatch_writesCouncilLiveAttemptWithAuthorityConfidence_bypassesLocal() {
        when(liveAdapterProvider.getIfAvailable()).thenReturn(liveAdapter);
        BigDecimal authorityConfidence = new BigDecimal("0.81");
        when(liveAdapter.verify(any(), any(), any()))
                .thenReturn(CouncilRegistryAdapter.CouncilVerificationOutcome
                        .registryMatch(authorityConfidence, "AUTH-REC-9"));

        var resp = service.createProfile(request());

        assertThat(resp.trustLevel()).isEqualTo(ExternalProviderCollaborationService.TRUST_COUNCIL_FOUND);
        assertThat(resp.councilRegistrationMatched()).isTrue();
        assertThat(resp.matchedProviderPublicId()).isEqualTo("AUTH-REC-9");

        // The COUNCIL_LIVE attempt carries the AUTHORITY's confidence verbatim (never fabricated).
        ArgumentCaptor<ExternalProviderVerificationAttemptEntity> cap =
                ArgumentCaptor.forClass(ExternalProviderVerificationAttemptEntity.class);
        verify(verificationRepository).save(cap.capture());
        ExternalProviderVerificationAttemptEntity attempt = cap.getValue();
        assertThat(attempt.getSourceType())
                .isEqualTo(ExternalProviderCollaborationService.SOURCE_COUNCIL_LIVE);
        assertThat(attempt.getConfidenceScore()).isEqualByComparingTo(authorityConfidence);
        assertThat(attempt.getOutcome()).isEqualTo("REGISTRY_MATCH");

        // Live audit row written; local imported-registry repo NOT consulted (bypassed).
        verify(adapterCallAuditRepository).save(any(CouncilAdapterCallAuditEntity.class));
        verifyNoInteractions(councilRegistrationRecordRepository);
    }

    // (3) Adapter ON + unreachable: honest degraded, NO pass, falls through to local, deny-when-unreachable respected.
    @Test
    void adapterEnabledButUnreachable_honestDegraded_noPass_fallsThroughToLocal() {
        properties.getCouncilRegulatory().setPolicyDenyWhenUnreachable(true);
        when(liveAdapterProvider.getIfAvailable()).thenReturn(liveAdapter);
        when(liveAdapter.verify(any(), any(), any()))
                .thenReturn(CouncilRegistryAdapter.CouncilVerificationOutcome.unreachable());
        // Local registry also misses ⇒ no fabricated match anywhere.
        when(councilRegistrationRecordRepository
                .findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenantId, councilId, "EC-12345"))
                .thenReturn(Optional.empty());

        var resp = service.createProfile(request());

        // Never a PASS: not VERIFIED, not matched. Falls through to the local NO_MATCH grade.
        assertThat(resp.verificationState()).isNotEqualTo("VERIFIED");
        assertThat(resp.councilRegistrationMatched()).isFalse();
        assertThat(resp.trustLevel()).isEqualTo(ExternalProviderCollaborationService.TRUST_COUNCIL_FORMAT);

        // Two attempts written: the honest COUNCIL_LIVE degraded one, then the local NOT_FOUND one.
        ArgumentCaptor<ExternalProviderVerificationAttemptEntity> cap =
                ArgumentCaptor.forClass(ExternalProviderVerificationAttemptEntity.class);
        verify(verificationRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        List<ExternalProviderVerificationAttemptEntity> attempts = cap.getAllValues();

        ExternalProviderVerificationAttemptEntity liveAttempt = attempts.get(0);
        assertThat(liveAttempt.getSourceType())
                .isEqualTo(ExternalProviderCollaborationService.SOURCE_COUNCIL_LIVE);
        assertThat(liveAttempt.getOutcome()).isEqualTo("UNREACHABLE");
        assertThat(liveAttempt.getConfidenceScore()).isNull(); // no fabricated score
        assertThat(liveAttempt.getNotes()).isEqualTo("DENIED_UNREACHABLE"); // deny-when-unreachable honoured

        // Audit records the honest degraded call.
        ArgumentCaptor<CouncilAdapterCallAuditEntity> auditCap =
                ArgumentCaptor.forClass(CouncilAdapterCallAuditEntity.class);
        verify(adapterCallAuditRepository).save(auditCap.capture());
        assertThat(auditCap.getValue().isReachable()).isFalse();
        assertThat(auditCap.getValue().getResult()).isEqualTo("UNREACHABLE");
    }

    // Adapter ON but NOT_CONFIGURED for this council ⇒ silent fall-through, no attempt, no audit.
    @Test
    void adapterEnabledButNotConfiguredForCouncil_fallsThroughSilently() {
        when(liveAdapterProvider.getIfAvailable()).thenReturn(liveAdapter);
        when(liveAdapter.verify(any(), any(), any()))
                .thenReturn(CouncilRegistryAdapter.CouncilVerificationOutcome.notConfigured());
        when(councilRegistrationRecordRepository
                .findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenantId, councilId, "EC-12345"))
                .thenReturn(Optional.of(localHit()));

        var resp = service.createProfile(request());

        // Local path took over unchanged.
        assertThat(resp.matchedProviderPublicId()).isEqualTo("PUB-LOCAL-1");
        verify(adapterCallAuditRepository, never()).save(any());
        ArgumentCaptor<ExternalProviderVerificationAttemptEntity> cap =
                ArgumentCaptor.forClass(ExternalProviderVerificationAttemptEntity.class);
        verify(verificationRepository).save(cap.capture());
        assertThat(cap.getValue().getSourceType()).isEqualTo("IMPORTED_REGISTRY");
    }
}
