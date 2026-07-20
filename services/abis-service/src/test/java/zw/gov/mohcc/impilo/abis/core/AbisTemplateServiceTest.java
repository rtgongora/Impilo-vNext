package zw.gov.mohcc.impilo.abis.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.abis.config.AbisThresholdProperties;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.abis.engine.FailClosedMatchingEngine;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.TemplateRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ABIS template custody + matching orchestration:
 * enroll persists an ENCRYPTED row + outbox event; verify fails closed (UNAVAILABLE);
 * identify never fabricates candidates and never produces a merge decision. Plus W3b
 * enrol-time dedup, W3d quality gating + versioning.
 */
class AbisTemplateServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String KEK_HEX =
            "101112131415161718191a1b1c1d1e1f000102030405060708090a0b0c0d0e0f";

    private TemplateRepository templateRepository;
    private EventOutboxRepository outboxRepository;
    private AdjudicationService adjudicationService;
    private AbisThresholdProperties thresholds;
    private TemplateCrypto crypto;
    private AbisTemplateService service;

    @BeforeEach
    void setUp() {
        templateRepository = mock(TemplateRepository.class);
        outboxRepository = mock(EventOutboxRepository.class);
        adjudicationService = mock(AdjudicationService.class);
        thresholds = new AbisThresholdProperties();
        crypto = new TemplateCrypto(KEK_HEX);
        BiometricMatchingEngine failClosed = new FailClosedMatchingEngine(0);
        service = new AbisTemplateService(
                templateRepository, outboxRepository, failClosed, crypto, new ObjectMapper(),
                adjudicationService, thresholds, 500);
        when(templateRepository.save(any(TemplateEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enrollPersistsEncryptedTemplateAndRecordsOutboxEvent() throws Exception {
        byte[] template = "probe-template-bytes".getBytes(StandardCharsets.UTF_8);
        when(templateRepository.findByTenantIdAndSubjectRefAndModalityAndPositionAndStatus(
                TENANT, "subj-opaque-1", BiometricModality.FINGERPRINT, "LEFT_INDEX", "ACTIVE"))
                .thenReturn(Optional.empty());

        AbisTemplateService.EnrollOutcome outcome = service.enroll(TENANT, "subj-opaque-1",
                BiometricModality.FINGERPRINT, "LEFT_INDEX", 87, "vendor-x-3.1", template);
        TemplateEntity saved = outcome.template();

        ArgumentCaptor<TemplateEntity> entityCaptor = ArgumentCaptor.forClass(TemplateEntity.class);
        verify(templateRepository).save(entityCaptor.capture());
        TemplateEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT);
        assertThat(persisted.getSubjectRef()).isEqualTo("subj-opaque-1");
        assertThat(persisted.getStatus()).isEqualTo("ACTIVE");
        assertThat(persisted.getVersion()).isEqualTo(1);
        // Stored bytes are encrypted, not the raw template, and round-trip through the KEK.
        assertThat(persisted.getTemplateEncrypted()).isNotEqualTo(template);
        assertThat(crypto.decrypt(persisted.getTemplateEncrypted())).isEqualTo(template);
        assertThat(saved.getModality()).isEqualTo(BiometricModality.FINGERPRINT);
        assertThat(outcome.hasDuplicateReview()).isFalse();

        ArgumentCaptor<EventOutboxEntity> eventCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(eventCaptor.capture());
        EventOutboxEntity event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("abis.template.enrolled");
        // Payload is a pre-serialized JSON string (StringSerializer estate law) with no template bytes.
        JsonNode payload = new ObjectMapper().readTree(event.getPayload());
        assertThat(payload.get("subjectRef").asText()).isEqualTo("subj-opaque-1");
        assertThat(event.getPayload()).doesNotContain("template");
    }

    @Test
    void reEnrolSupersedesPriorActiveKeepingVersionHistory() {
        TemplateEntity prior = new TemplateEntity();
        prior.setTenantId(TENANT);
        prior.setSubjectRef("subj-opaque-1");
        prior.setModality(BiometricModality.FINGERPRINT);
        prior.setPosition("PRIMARY");
        prior.setStatus("ACTIVE");
        prior.setVersion(3);
        when(templateRepository.findByTenantIdAndSubjectRefAndModalityAndPositionAndStatus(
                TENANT, "subj-opaque-1", BiometricModality.FINGERPRINT, "PRIMARY", "ACTIVE"))
                .thenReturn(Optional.of(prior));

        AbisTemplateService.EnrollOutcome outcome = service.enroll(TENANT, "subj-opaque-1",
                BiometricModality.FINGERPRINT, null, 90, "vendor-x-3.1",
                "new-template".getBytes(StandardCharsets.UTF_8));

        // Prior is superseded (kept as history), new row carries version+1 and stays ACTIVE.
        assertThat(prior.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(outcome.template().getVersion()).isEqualTo(4);
        assertThat(outcome.template().getStatus()).isEqualTo("ACTIVE");
        // Two saves: supersede the prior + insert the new version.
        verify(templateRepository, times(2)).save(any(TemplateEntity.class));
    }

    @Test
    void enrollRejectsBelowPerModalityQualityThreshold() {
        thresholds.getQuality().getMinScore().put("FINGERPRINT", 60);

        assertThatThrownBy(() -> service.enroll(TENANT, "subj-low-q",
                BiometricModality.FINGERPRINT, null, 42, "vendor-x-3.1",
                "t".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(QualityBelowThresholdException.class);

        // Fail-closed: a degraded reference never enters the gallery.
        verify(templateRepository, never()).save(any(TemplateEntity.class));
    }

    @Test
    void enrolTimeDedupOpensCaseWhenCandidateAboveThresholdButNeverMerges() {
        thresholds.getDedup().setEnabled(true);
        thresholds.getDedup().setThreshold(0.8);

        BiometricMatchingEngine engine = mock(BiometricMatchingEngine.class);
        TemplateEntity other = new TemplateEntity();
        other.setTenantId(TENANT);
        other.setSubjectRef("existing-subj");
        other.setModality(BiometricModality.FINGERPRINT);
        other.setStatus("ACTIVE");
        when(templateRepository.findByTenantIdAndModalityAndStatus(
                eq(TENANT), eq(BiometricModality.FINGERPRINT), eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(List.of(other));
        when(engine.identify(any(), any(), any()))
                .thenReturn(List.of(new IdentificationCandidate("existing-subj", 0.93, "engine match")));
        AdjudicationCaseEntity opened = new AdjudicationCaseEntity();
        opened.setId(77L);
        opened.setStatus("OPEN");
        opened.setCandidateCount(1);
        when(adjudicationService.openCase(eq(TENANT), eq("new-subj"),
                eq(BiometricModality.FINGERPRINT), eq("ENROLMENT"), any()))
                .thenReturn(opened);

        AbisTemplateService dedupService = new AbisTemplateService(
                templateRepository, outboxRepository, engine, crypto, new ObjectMapper(),
                adjudicationService, thresholds, 500);

        AbisTemplateService.EnrollOutcome outcome = dedupService.enroll(TENANT, "new-subj",
                BiometricModality.FINGERPRINT, null, 88, "vendor-x-3.1",
                "probe".getBytes(StandardCharsets.UTF_8));

        // A duplicate case was opened (never a merge) AND the enrol still landed (care-first).
        assertThat(outcome.hasDuplicateReview()).isTrue();
        assertThat(outcome.duplicateCase().getId()).isEqualTo(77L);
        assertThat(outcome.template().getStatus()).isEqualTo("ACTIVE");
        verify(adjudicationService).openCase(eq(TENANT), eq("new-subj"),
                eq(BiometricModality.FINGERPRINT), eq("ENROLMENT"), any());
    }

    @Test
    void verifyFailsClosedWithUnavailableWhenNoEngineConfigured() {
        TemplateEntity enrolled = new TemplateEntity();
        enrolled.setTenantId(TENANT);
        enrolled.setSubjectRef("subj-opaque-1");
        enrolled.setModality(BiometricModality.FINGERPRINT);
        enrolled.setTemplateEncrypted(crypto.encrypt("stored".getBytes(StandardCharsets.UTF_8)));
        when(templateRepository.findFirstByTenantIdAndSubjectRefAndModalityAndStatusOrderByUpdatedAtDesc(
                TENANT, "subj-opaque-1", BiometricModality.FINGERPRINT, "ACTIVE"))
                .thenReturn(Optional.of(enrolled));

        VerificationDecision decision = service.verify(TENANT, "subj-opaque-1",
                BiometricModality.FINGERPRINT, null, "probe".getBytes(StandardCharsets.UTF_8), null);

        assertThat(decision.result()).isEqualTo("UNAVAILABLE");
        assertThat(decision.confidence()).isZero();

        ArgumentCaptor<EventOutboxEntity> eventCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("abis.verify.performed");
    }

    @Test
    void verifyReturnsNoReferenceWhenSubjectHasNoEnrolledTemplate() {
        when(templateRepository.findFirstByTenantIdAndSubjectRefAndModalityAndStatusOrderByUpdatedAtDesc(
                TENANT, "unknown-subject", BiometricModality.FACE, "ACTIVE"))
                .thenReturn(Optional.empty());

        VerificationDecision decision = service.verify(TENANT, "unknown-subject",
                BiometricModality.FACE, null, "probe".getBytes(StandardCharsets.UTF_8), null);

        assertThat(decision.result()).isEqualTo("NO_REFERENCE");
        assertThat(decision.confidence()).isZero();
    }

    @Test
    void identifyFailsClosedWithNoCandidatesAndRecordsAdjudicationDecision() throws Exception {
        TemplateEntity candidate = new TemplateEntity();
        candidate.setTenantId(TENANT);
        candidate.setSubjectRef("subj-opaque-2");
        candidate.setModality(BiometricModality.FINGERPRINT);
        candidate.setTemplateEncrypted(crypto.encrypt("stored".getBytes(StandardCharsets.UTF_8)));
        when(templateRepository.findByTenantIdAndModalityAndStatus(
                any(), any(), any(), any())).thenReturn(List.of(candidate));

        List<IdentificationCandidate> candidates = service.identify(TENANT,
                BiometricModality.FINGERPRINT, "probe".getBytes(StandardCharsets.UTF_8),
                "DEDUPLICATION", null);

        // Fail-closed engine never fabricates candidates.
        assertThat(candidates).isEmpty();

        ArgumentCaptor<EventOutboxEntity> eventCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(eventCaptor.capture());
        EventOutboxEntity event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("abis.identify.performed");
        JsonNode payload = new ObjectMapper().readTree(event.getPayload());
        // NEVER an automatic merge — identification only ever yields candidates for adjudication.
        assertThat(payload.get("decision").asText()).isEqualTo("CANDIDATES_FOR_ADJUDICATION");
    }
}
