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
import zw.gov.mohcc.impilo.varapi.api.dto.trust.ProviderTrustStatusResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.RecordVerificationAttemptRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.TrustEvidenceDto;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.TrustTransitionRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.TrustTransitionResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.trust.VerificationAttemptView;
import zw.gov.mohcc.impilo.varapi.core.ProviderTrustService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderVerificationAttemptEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderVerificationAttemptRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Frozen trust API contract (WS-D/WS-F compile against these DTO shapes) and the
 * council-number leak guard: every response DTO is serialized with Jackson and the
 * resulting JSON asserted to contain NO council registration number and NO full
 * platform Provider ID — council numbers are matching evidence, not identity.
 */
@ExtendWith(MockitoExtension.class)
class ProviderTrustControllerTest {

    private static final String COUNCIL_NUMBER = "MDPCZ-778899";
    private static final String PROVIDER_PUBLIC_ID = "ZXCVB1234567890QWERTY12345";

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderVerificationAttemptRepository attemptRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ProviderTrustController controller;
    private ProviderEntity provider;
    private final UUID tenantId = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        controller = new ProviderTrustController(
                new ProviderTrustService(providerRepository, attemptRepository, outboxRepository));
        TrustContextHolder.set(new TrustContext(
                tenantId, "registry-admin-1", "OPERATOR", "REGISTRY_ADMIN", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));

        provider = new ProviderEntity();
        provider.setId(42L);
        provider.setTenantId(tenantId);
        provider.setProviderPublicId(PROVIDER_PUBLIC_ID);
        provider.setPracticeNumber(COUNCIL_NUMBER);
        provider.setTrustLevel("SELF_ASSERTED");

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

    // ── POST /trust — masked public id, no council number ────────────────────

    @Test
    void postTrust_returnsMaskedProviderPublicId_andNoCouncilNumber() throws Exception {
        TrustTransitionRequest request = new TrustTransitionRequest(
                "COUNCIL_VERIFIED",
                // Caller passes the council number as evidence ref — it must never come back.
                new TrustEvidenceDto("COUNCIL_REGISTRATION", "COUNCIL_RECORD",
                        COUNCIL_NUMBER, BigDecimal.valueOf(0.92)),
                "REGULATORY_A");

        TrustTransitionResponse body = controller.applyTransition("42", request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.trustLevel()).isEqualTo("COUNCIL_VERIFIED");
        assertThat(body.registryStatus()).isEqualTo("REGISTERED_ACTIVE");
        // MASKED: first 4 chars + ***
        assertThat(body.providerPublicId()).isEqualTo("ZXCV***");

        String serialized = json.writeValueAsString(body);
        assertThat(serialized).doesNotContain(COUNCIL_NUMBER);
        assertThat(serialized).doesNotContain(PROVIDER_PUBLIC_ID);
    }

    // ── GET /trust — attempts carry masked refs only ─────────────────────────

    @Test
    void getTrust_neverLeaksCouncilNumbers_evenViaEvidenceRefs() throws Exception {
        provider.setTrustLevel("COUNCIL_VERIFIED");
        provider.setRegistryStatus("MATCHED_BOTH");
        provider.setOnboardingChannel("REGULATORY_A");

        ProviderVerificationAttemptEntity attempt = new ProviderVerificationAttemptEntity();
        attempt.setTenantId(tenantId);
        attempt.setProvider(provider);
        attempt.setEvidenceType("COUNCIL_REGISTRATION");
        attempt.setEvidenceSource("COUNCIL_RECORD");
        attempt.setOutcome("TRUST_UPGRADED");
        attempt.setConfidence(BigDecimal.valueOf(0.92));
        attempt.setEvidenceRef(COUNCIL_NUMBER); // stored in full, masked on the way out
        attempt.setAttemptedBy("registry-admin-1");
        attempt.setAttemptedAt(java.time.Instant.parse("2026-07-01T10:15:30Z"));
        lenient().when(attemptRepository
                .findByTenantIdAndProvider_IdOrderByAttemptedAtDescIdDesc(tenantId, 42L))
                .thenReturn(List.of(attempt));

        ProviderTrustStatusResponse body = controller.getTrust("42").getBody();

        assertThat(body).isNotNull();
        assertThat(body.trustLevel()).isEqualTo("COUNCIL_VERIFIED");
        assertThat(body.registryStatus()).isEqualTo("MATCHED_BOTH");
        assertThat(body.onboardingChannel()).isEqualTo("REGULATORY_A");
        assertThat(body.attempts()).hasSize(1);
        assertThat(body.attempts().get(0).ref()).isEqualTo("MDPC***");

        String serialized = json.writeValueAsString(body);
        assertThat(serialized).doesNotContain(COUNCIL_NUMBER);
        assertThat(serialized).doesNotContain(PROVIDER_PUBLIC_ID);
    }

    // ── POST /verification-attempts — recorded, echoed masked ────────────────

    @Test
    void postVerificationAttempt_recordsRow_andEchoesMaskedRefOnly() throws Exception {
        VerificationAttemptView body = controller.recordAttempt("42",
                new RecordVerificationAttemptRequest(
                        "COUNCIL_REGISTRATION", "COUNCIL_RECORD", COUNCIL_NUMBER,
                        "NOT_FOUND", BigDecimal.valueOf(0.25))).getBody();

        assertThat(body).isNotNull();
        assertThat(body.outcome()).isEqualTo("NOT_FOUND");
        assertThat(body.source()).isEqualTo("COUNCIL_RECORD");
        assertThat(body.ref()).isEqualTo("MDPC***");

        String serialized = json.writeValueAsString(body);
        assertThat(serialized).doesNotContain(COUNCIL_NUMBER);
    }

    @Test
    void maskKeepsAtMostFourLeadingCharacters() {
        assertThat(ProviderTrustController.mask("MDPCZ-778899")).isEqualTo("MDPC***");
        assertThat(ProviderTrustController.mask("AB12")).isEqualTo("AB1***");
        assertThat(ProviderTrustController.mask("A")).isEqualTo("***");
        assertThat(ProviderTrustController.mask("  ")).isNull();
        assertThat(ProviderTrustController.mask(null)).isNull();
    }
}
