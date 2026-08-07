package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.enums.OnboardingChannel;
import zw.gov.mohcc.impilo.varapi.enums.ProviderRegistryStatus;
import zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderVerificationAttemptEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderVerificationAttemptRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trust ladder rules (IATG Wave 1): monotonic upgrades, the explicit
 * evidence-revoked downgrade path, one verification-attempt row per transition,
 * and coherent registry-status composition (MATCHED_EMPLOYMENT_ONLY / MATCHED_BOTH).
 */
@ExtendWith(MockitoExtension.class)
class ProviderTrustServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderVerificationAttemptRepository attemptRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ProviderTrustService service;

    private final UUID tenantId = UUID.randomUUID();
    private ProviderEntity provider;

    @BeforeEach
    void setUp() {
        service = new ProviderTrustService(providerRepository, attemptRepository, outboxRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, "registry-admin-1", "OPERATOR", "REGISTRY_ADMIN", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(tenantId);
        provider.setProviderPublicId("ABCDEF1234567890ABCDEF1234");
        provider.setTrustLevel(ProviderTrustLevel.SELF_ASSERTED.name());

        lenient().when(providerRepository.findByIdAndTenantId(42L, tenantId))
                .thenReturn(Optional.of(provider));
        lenient().when(providerRepository.save(any(ProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(attemptRepository.save(any(ProviderVerificationAttemptEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProviderTrustService.TrustEvidence evidence(String type, String source, String ref) {
        return new ProviderTrustService.TrustEvidence(type, source, ref, BigDecimal.valueOf(0.9));
    }

    // ── Monotonic upgrades ────────────────────────────────────────────────────

    @Test
    void employmentEvidenceUpgrades_andYieldsMatchedEmploymentOnly() {
        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.EMPLOYMENT_MATCHED,
                evidence("EMPLOYMENT_RECORD", "HSC_EMPLOYMENT", "hsc-rec-1"),
                OnboardingChannel.WORKFORCE_B);

        assertThat(result.trustLevel()).isEqualTo(ProviderTrustLevel.EMPLOYMENT_MATCHED);
        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.MATCHED_EMPLOYMENT_ONLY);
        assertThat(provider.getTrustLevel()).isEqualTo("EMPLOYMENT_MATCHED");
        assertThat(provider.getRegistryStatus()).isEqualTo("MATCHED_EMPLOYMENT_ONLY");
        assertThat(provider.getOnboardingChannel()).isEqualTo("WORKFORCE_B");

        // EVERY transition writes exactly one attempt row.
        ArgumentCaptor<ProviderVerificationAttemptEntity> captor =
                ArgumentCaptor.forClass(ProviderVerificationAttemptEntity.class);
        verify(attemptRepository).save(captor.capture());
        ProviderVerificationAttemptEntity row = captor.getValue();
        assertThat(row.getOutcome()).isEqualTo(ProviderTrustService.OUTCOME_TRUST_UPGRADED);
        assertThat(row.getEvidenceSource()).isEqualTo("HSC_EMPLOYMENT");
        assertThat(row.getAttemptedBy()).isEqualTo("registry-admin-1");
        assertThat(row.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void councilAfterEmployment_composesMatchedBoth() {
        provider.setTrustLevel(ProviderTrustLevel.EMPLOYMENT_MATCHED.name());
        provider.setRegistryStatus(ProviderRegistryStatus.MATCHED_EMPLOYMENT_ONLY.name());

        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.COUNCIL_VERIFIED,
                evidence("COUNCIL_REGISTRATION", "COUNCIL_RECORD", "reg-match-9"), null);

        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.MATCHED_BOTH);
        assertThat(provider.getRegistryStatus()).isEqualTo("MATCHED_BOTH");
    }

    @Test
    void employmentAfterCouncil_alsoComposesMatchedBoth() {
        provider.setTrustLevel(ProviderTrustLevel.COUNCIL_VERIFIED.name());
        provider.setRegistryStatus(ProviderRegistryStatus.REGISTERED_ACTIVE.name());

        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.COUNCIL_VERIFIED,
                evidence("EMPLOYMENT_RECORD", "HSC_EMPLOYMENT", "hsc-rec-2"), null);

        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.MATCHED_BOTH);
        // Same-level transition is a reaffirmation, still evidenced.
        ArgumentCaptor<ProviderVerificationAttemptEntity> captor =
                ArgumentCaptor.forClass(ProviderVerificationAttemptEntity.class);
        verify(attemptRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome())
                .isEqualTo(ProviderTrustService.OUTCOME_TRUST_REAFFIRMED);
    }

    @Test
    void councilOnlyMatch_yieldsRegisteredActive() {
        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.COUNCIL_VERIFIED,
                evidence("COUNCIL_REGISTRATION", "COUNCIL_RECORD", "reg-match-1"), null);

        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.REGISTERED_ACTIVE);
    }

    @Test
    void documentEvidenceNeverFabricatesRegistryMatch() {
        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.DOCUMENT_SUPPORTED,
                evidence("QUALIFICATION_DOCUMENT", "DOCUMENT", "doc-77"), null);

        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.PENDING_VERIFICATION);
    }

    // ── Monotonicity: no silent downgrade ────────────────────────────────────

    @Test
    void downgradeWithoutRevokedEvidenceIsRejected() {
        provider.setTrustLevel(ProviderTrustLevel.COUNCIL_VERIFIED.name());
        provider.setRegistryStatus(ProviderRegistryStatus.REGISTERED_ACTIVE.name());

        assertThatThrownBy(() -> service.applyTransition(
                "42", ProviderTrustLevel.SELF_ASSERTED,
                evidence("COUNCIL_REGISTRATION", "COUNCIL_RECORD", "whatever"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("monotonic");

        // Nothing mutated, nothing recorded.
        assertThat(provider.getTrustLevel()).isEqualTo("COUNCIL_VERIFIED");
        verify(providerRepository, never()).save(any());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void revokePathRequiresReason() {
        provider.setTrustLevel(ProviderTrustLevel.COUNCIL_VERIFIED.name());

        assertThatThrownBy(() -> service.applyTransition(
                "42", ProviderTrustLevel.SELF_ASSERTED,
                evidence(ProviderTrustService.EVIDENCE_TYPE_REVOKED, "COUNCIL_RECORD", "  "), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reason");
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void explicitEvidenceRevokedPathDowngrades_withAuditRow() {
        provider.setTrustLevel(ProviderTrustLevel.COUNCIL_VERIFIED.name());
        provider.setRegistryStatus(ProviderRegistryStatus.MATCHED_BOTH.name());

        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "42", ProviderTrustLevel.SELF_ASSERTED,
                evidence(ProviderTrustService.EVIDENCE_TYPE_REVOKED, "COUNCIL_RECORD",
                        "Council record withdrawn after fraud investigation"), null);

        assertThat(result.trustLevel()).isEqualTo(ProviderTrustLevel.SELF_ASSERTED);
        assertThat(result.registryStatus()).isEqualTo(ProviderRegistryStatus.PENDING_VERIFICATION);

        ArgumentCaptor<ProviderVerificationAttemptEntity> captor =
                ArgumentCaptor.forClass(ProviderVerificationAttemptEntity.class);
        verify(attemptRepository).save(captor.capture());
        ProviderVerificationAttemptEntity row = captor.getValue();
        assertThat(row.getOutcome()).isEqualTo(ProviderTrustService.OUTCOME_TRUST_REVOKED);
        assertThat(row.getEvidenceType()).isEqualTo(ProviderTrustService.EVIDENCE_TYPE_REVOKED);
        assertThat(row.getEvidenceRef()).contains("fraud investigation");
    }

    @Test
    void revokedEvidenceCannotAccompanyAnUpgrade() {
        assertThatThrownBy(() -> service.applyTransition(
                "42", ProviderTrustLevel.COUNCIL_VERIFIED,
                evidence(ProviderTrustService.EVIDENCE_TYPE_REVOKED, "COUNCIL_RECORD", "reason"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot accompany");
    }

    // ── Guardrails ───────────────────────────────────────────────────────────

    @Test
    void unknownEvidenceSourceIsRejected() {
        assertThatThrownBy(() -> service.applyTransition(
                "42", ProviderTrustLevel.DOCUMENT_SUPPORTED,
                evidence("DOC", "SOME_RANDOM_SOURCE", "x"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("evidence.source");
    }

    @Test
    void channelNeverOverwritesExistingChannel() {
        provider.setOnboardingChannel(OnboardingChannel.REGULATORY_A.name());

        service.applyTransition("42", ProviderTrustLevel.DOCUMENT_SUPPORTED,
                evidence("DOC", "DOCUMENT", "doc-1"), OnboardingChannel.SELF);

        assertThat(provider.getOnboardingChannel()).isEqualTo("REGULATORY_A");
    }

    @Test
    void resolvesProviderByPublicIdToo() {
        when(providerRepository.findByProviderPublicIdAndTenantId("ABCDEF1234567890ABCDEF1234", tenantId))
                .thenReturn(Optional.of(provider));

        ProviderTrustService.TrustTransitionResult result = service.applyTransition(
                "ABCDEF1234567890ABCDEF1234", ProviderTrustLevel.DOCUMENT_SUPPORTED,
                evidence("DOC", "DOCUMENT", "doc-2"), null);

        assertThat(result.trustLevel()).isEqualTo(ProviderTrustLevel.DOCUMENT_SUPPORTED);
    }

    @Test
    void recordAttempt_writesRowWithoutMovingTrust() {
        ProviderVerificationAttemptEntity row = service.recordAttempt(
                "42", "COUNCIL_REGISTRATION", "COUNCIL_RECORD", "MDPCZ-778899",
                "NOT_FOUND", BigDecimal.valueOf(0.25));

        assertThat(row.getOutcome()).isEqualTo("NOT_FOUND");
        assertThat(row.getEvidenceSource()).isEqualTo("COUNCIL_RECORD");
        // Trust untouched by raw evidence recording.
        assertThat(provider.getTrustLevel()).isEqualTo("SELF_ASSERTED");
        verify(providerRepository, never()).save(any());
    }
}
