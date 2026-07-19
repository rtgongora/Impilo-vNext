package zw.gov.mohcc.impilo.vito.core.biometric.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ABIS-delegating engine must FAIL CLOSED on any transport error: 1:1 verification
 * returns UNAVAILABLE at zero confidence, 1:N identification returns no candidates. A broken
 * ABIS link must never fabricate or leak a match.
 */
class AbisDelegatingMatchingEngineTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final byte[] PROBE = "probe".getBytes(StandardCharsets.UTF_8);

    private RestTemplate restTemplate;
    private AbisDelegatingMatchingEngine engine;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        engine = new AbisDelegatingMatchingEngine(restTemplate, "http://abis-service:8186");
    }

    private static BiometricTemplateEntity enrolled(UUID healthId, UUID profileId) {
        BiometricTemplateEntity entity = new BiometricTemplateEntity();
        entity.setTenantId(TENANT);
        entity.setHealthId(healthId);
        entity.setProfileId(profileId);
        entity.setModality(BiometricModality.FINGERPRINT);
        entity.setPosition("LEFT_INDEX");
        return entity;
    }

    @Test
    void verifyFailsClosedWithUnavailableOnTransportError() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(AbisDelegatingMatchingEngine.AbisVerifyResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        VerificationDecision decision =
                engine.verify(PROBE, enrolled(UUID.randomUUID(), UUID.randomUUID()), BiometricProbeContext.EMPTY);

        assertThat(decision.result()).isEqualTo("UNAVAILABLE");
        assertThat(decision.confidence()).isZero();
        assertThat(decision.summary()).contains("fails closed");
    }

    @Test
    void identifyFailsClosedWithNoCandidatesOnTransportError() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(AbisDelegatingMatchingEngine.AbisIdentifyResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        List<IdentificationCandidate> candidates = engine.identify(
                PROBE, List.of(enrolled(UUID.randomUUID(), UUID.randomUUID())), BiometricProbeContext.EMPTY);

        assertThat(candidates).isEmpty();
    }

    @Test
    void verifyMapsAbisDecisionThrough() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(AbisDelegatingMatchingEngine.AbisVerifyResponse.class)))
                .thenReturn(ResponseEntity.ok(new AbisDelegatingMatchingEngine.AbisVerifyResponse(
                        "UNAVAILABLE", 0.0, "no vendor engine configured")));

        VerificationDecision decision =
                engine.verify(PROBE, enrolled(UUID.randomUUID(), UUID.randomUUID()), BiometricProbeContext.EMPTY);

        assertThat(decision.result()).isEqualTo("UNAVAILABLE");
        assertThat(decision.confidence()).isZero();
    }

    @Test
    void identifyMapsOpaqueSubjectRefsBackToHealthIdsAndDropsUnknownRefs() {
        UUID healthId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(AbisDelegatingMatchingEngine.AbisIdentifyResponse.class)))
                .thenReturn(ResponseEntity.ok(new AbisDelegatingMatchingEngine.AbisIdentifyResponse(
                        "DEDUPLICATION", "CANDIDATES_FOR_ADJUDICATION",
                        List.of(
                                new AbisDelegatingMatchingEngine.AbisIdentifyCandidate(
                                        profileId.toString(), 0.93, "vendor match"),
                                new AbisDelegatingMatchingEngine.AbisIdentifyCandidate(
                                        "unknown-subject-ref", 0.88, "outside supplied candidate set")))));

        List<IdentificationCandidate> candidates = engine.identify(
                PROBE, List.of(enrolled(healthId, profileId)), BiometricProbeContext.EMPTY);

        // The known opaque ref maps back to the candidate's health id; unknown refs are dropped
        // (candidates for adjudication only — never an automatic merge, no cross-population leak).
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).healthId()).isEqualTo(healthId);
        assertThat(candidates.get(0).confidence()).isEqualTo(0.93);
    }

    @Test
    void verifyWithMissingProbeOrReferenceFailsClosedWithoutCallingAbis() {
        assertThat(engine.verify(null, enrolled(UUID.randomUUID(), null), BiometricProbeContext.EMPTY)
                .result()).isEqualTo("NO_REFERENCE");
        assertThat(engine.verify(PROBE, null, BiometricProbeContext.EMPTY)
                .result()).isEqualTo("NO_REFERENCE");
        assertThat(engine.identify(PROBE, List.of(), BiometricProbeContext.EMPTY)).isEmpty();
    }
}
