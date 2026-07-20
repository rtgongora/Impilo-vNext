package zw.gov.mohcc.impilo.abis.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.DuplicateInvestigationEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;
import zw.gov.mohcc.impilo.abis.persistence.repository.AdjudicationCaseRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.abis.persistence.repository.TemplateRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adjudication platform (W3c): cases open (never merge) on candidate matches; a merge is a
 * governed, dual-control action that is NEVER automatic.
 */
class AdjudicationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private AdjudicationCaseRepository caseRepository;
    private TemplateRepository templateRepository;
    private EventOutboxRepository outboxRepository;
    private AdjudicationService service;

    @BeforeEach
    void setUp() {
        caseRepository = mock(AdjudicationCaseRepository.class);
        templateRepository = mock(TemplateRepository.class);
        outboxRepository = mock(EventOutboxRepository.class);
        service = new AdjudicationService(
                caseRepository, templateRepository, outboxRepository, new ObjectMapper());
        when(caseRepository.save(any(AdjudicationCaseEntity.class))).thenAnswer(inv -> {
            AdjudicationCaseEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(101L);
            }
            return c;
        });
    }

    @Test
    void openCaseStoresCandidatesTopScoreAndEmitsEvent() {
        List<IdentificationCandidate> candidates = List.of(
                new IdentificationCandidate("cand-a", 0.91, "match a"),
                new IdentificationCandidate("cand-b", 0.83, "match b"));

        AdjudicationCaseEntity kase = service.openCase(
                TENANT, "probe-subj", BiometricModality.FINGERPRINT, "ENROLMENT", candidates);

        assertThat(kase.getStatus()).isEqualTo("OPEN");
        assertThat(kase.getCandidateCount()).isEqualTo(2);
        assertThat(kase.getTopScore()).isEqualTo(0.91);
        assertThat(kase.getDecision()).isNull();
        assertThat(kase.getCandidates()).extracting(DuplicateInvestigationEntity::getCandidateSubjectRef)
                .containsExactly("cand-a", "cand-b");

        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("abis.adjudication.case.opened");
    }

    @Test
    void recordDecisionRequiresReviewerAndValidDecision() {
        AdjudicationCaseEntity kase = openCase();
        when(caseRepository.findByIdAndTenantId(101L, TENANT)).thenReturn(Optional.of(kase));

        assertThatThrownBy(() -> service.recordDecision(TENANT, 101L, "CONFIRMED_DUPLICATE", "  ", "dup"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordDecision(TENANT, 101L, "MAYBE", "reviewer-a", "dup"))
                .isInstanceOf(IllegalArgumentException.class);

        AdjudicationCaseEntity resolved =
                service.recordDecision(TENANT, 101L, "CONFIRMED_DUPLICATE", "reviewer-a", "clear dup");
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getDecision()).isEqualTo("CONFIRMED_DUPLICATE");
        assertThat(resolved.getDecidedBy()).isEqualTo("reviewer-a");
        assertThat(resolved.getMergedAt()).as("recording a decision NEVER merges").isNull();
    }

    @Test
    void mergeRequiresConfirmedDuplicateDecision() {
        AdjudicationCaseEntity kase = openCase();
        kase.setDecision("DISTINCT");
        kase.setDecidedBy("reviewer-a");
        kase.setStatus("RESOLVED");
        when(caseRepository.findByIdAndTenantId(101L, TENANT)).thenReturn(Optional.of(kase));

        assertThatThrownBy(() -> service.merge(TENANT, 101L, "cand-a", "reviewer-b"))
                .isInstanceOf(IllegalStateException.class);
        verify(templateRepository, never()).saveAll(any());
    }

    @Test
    void mergeEnforcesDualControl() {
        AdjudicationCaseEntity kase = openCase();
        kase.setDecision("CONFIRMED_DUPLICATE");
        kase.setDecidedBy("reviewer-a");
        kase.setStatus("RESOLVED");
        when(caseRepository.findByIdAndTenantId(101L, TENANT)).thenReturn(Optional.of(kase));

        // Same reviewer who recorded the decision cannot also sign off the merge.
        assertThatThrownBy(() -> service.merge(TENANT, 101L, "cand-a", "reviewer-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dual control");
        verify(templateRepository, never()).saveAll(any());
    }

    @Test
    void mergeRejectsSurvivorNotAmongCandidates() {
        AdjudicationCaseEntity kase = openCase();
        kase.setDecision("CONFIRMED_DUPLICATE");
        kase.setDecidedBy("reviewer-a");
        when(caseRepository.findByIdAndTenantId(101L, TENANT)).thenReturn(Optional.of(kase));

        assertThatThrownBy(() -> service.merge(TENANT, 101L, "not-a-candidate", "reviewer-b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void governedMergeMarksMergedTemplatesAndEmitsEvent() {
        AdjudicationCaseEntity kase = openCase();
        kase.setDecision("CONFIRMED_DUPLICATE");
        kase.setDecidedBy("reviewer-a");
        kase.setStatus("RESOLVED");
        when(caseRepository.findByIdAndTenantId(101L, TENANT)).thenReturn(Optional.of(kase));

        TemplateEntity probeTemplate = new TemplateEntity();
        probeTemplate.setSubjectRef("probe-subj");
        probeTemplate.setStatus("ACTIVE");
        when(templateRepository.findByTenantIdAndSubjectRefAndStatus(TENANT, "probe-subj", "ACTIVE"))
                .thenReturn(List.of(probeTemplate));

        AdjudicationCaseEntity merged = service.merge(TENANT, 101L, "cand-a", "reviewer-b");

        assertThat(merged.getMergeTargetSubjectRef()).isEqualTo("cand-a");
        assertThat(merged.getDualSignOffBy()).isEqualTo("reviewer-b");
        assertThat(merged.getMergedAt()).isNotNull();
        // The merged-away probe subject's templates are marked MERGED (superseded).
        assertThat(probeTemplate.getStatus()).isEqualTo("MERGED");
        verify(templateRepository).saveAll(any());

        ArgumentCaptor<EventOutboxEntity> ev = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("abis.adjudication.merge.performed");
    }

    private AdjudicationCaseEntity openCase() {
        AdjudicationCaseEntity kase = new AdjudicationCaseEntity();
        kase.setId(101L);
        kase.setTenantId(TENANT);
        kase.setSubjectRef("probe-subj");
        kase.setModality("FINGERPRINT");
        kase.setReason("ENROLMENT");
        kase.setStatus("OPEN");
        DuplicateInvestigationEntity cand = new DuplicateInvestigationEntity();
        cand.setCandidateSubjectRef("cand-a");
        cand.setScore(0.91);
        cand.setRank(0);
        kase.addCandidate(cand);
        kase.setCandidateCount(1);
        return kase;
    }
}
