package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The problem list is the record the whole Adult Medicine pack composes over, so what it does with
 * a field nobody filled in matters as much as what it does with one they did.
 */
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private ProblemService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProblemService(problemRepository, outboxRepository, new ObjectMapper(), accessGuard);
        // Stand in for @PrePersist, which JPA fires on save and a mocked repository does not.
        // Lenient because the vocabulary-rejection tests never reach a save — which is the point:
        // an unrecognised value is refused before anything is written.
        lenient().when(problemRepository.save(any())).thenAnswer(inv -> {
            ProblemEntity saved = inv.getArgument(0);
            if (saved.getProblemId() == null) saved.setProblemId(UUID.randomUUID());
            return saved;
        });
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    private ProblemEntity add(Map<String, Object> body) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            return service.add(body);
        }
    }

    private static Map<String, Object> problem() {
        Map<String, Object> body = new HashMap<>();
        body.put("subject_cpid", "CPID-9");
        body.put("display", "Hypertension");
        body.put("code", "I10");
        body.put("code_system", "ICD-10");
        return body;
    }

    @Test
    void recordsSeverityAgainstTheProblem() {
        Map<String, Object> body = problem();
        body.put("severity", "moderate");
        ProblemEntity p = add(body);
        assertThat(p.getSeverity()).isEqualTo("MODERATE");
        assertThat(p.getDisplay()).isEqualTo("Hypertension");
        assertThat(p.getCode()).isEqualTo("I10");
    }

    /**
     * The clinical point of the column. An unstated severity has to stay unstated: defaulting it
     * would write down an assessment nobody made, and of the available defaults "mild" is the one
     * that would stop the next reader looking any further.
     */
    @Test
    void leavesAnUnstatedSeverityUnstated() {
        assertThat(add(problem()).getSeverity()).isNull();
        Map<String, Object> blank = problem();
        blank.put("severity", "   ");
        assertThat(add(blank).getSeverity()).isNull();
    }

    /**
     * The author is the authenticated actor, never a value the caller supplied. A forgeable author
     * would let a diagnosis be attributed to a clinician who never made it.
     */
    @Test
    void stampsTheAuthorFromTheTrustContextAndIgnoresTheBody() {
        Map<String, Object> body = problem();
        body.put("recorded_by", "someone-else");
        assertThat(add(body).getRecordedBy()).isEqualTo("clinician-1");
    }

    @Test
    void defaultsStatusToActiveAndCategoryToDiagnosis() {
        ProblemEntity p = add(problem());
        assertThat(p.getClinicalStatus()).isEqualTo("ACTIVE");
        assertThat(p.getCategory()).isEqualTo("DIAGNOSIS");
    }

    // ── Certainty is a separate axis from kind ──────────────────────────────────

    /**
     * The distinction the whole model exists for. These two facts have to be recordable together —
     * folding certainty into the category would make "definitely has chest pain, might have an MI"
     * inexpressible, which is how a differential ends up on a record looking like a conclusion.
     */
    @Test
    void recordsAConfirmedSymptomAndASuspectedDiagnosisAsDifferentThings() {
        Map<String, Object> symptom = problem();
        symptom.put("display", "Chest pain");
        symptom.put("category", "SYMPTOM");
        symptom.put("diagnostic_certainty", "CONFIRMED");
        ProblemEntity s = add(symptom);

        Map<String, Object> diagnosis = problem();
        diagnosis.put("display", "Acute coronary syndrome");
        diagnosis.put("category", "DIAGNOSIS");
        diagnosis.put("diagnostic_certainty", "SUSPECTED");
        ProblemEntity d = add(diagnosis);

        assertThat(s.getCategory()).isEqualTo("SYMPTOM");
        assertThat(s.getDiagnosticCertainty()).isEqualTo("CONFIRMED");
        assertThat(d.getCategory()).isEqualTo("DIAGNOSIS");
        assertThat(d.getDiagnosticCertainty()).isEqualTo("SUSPECTED");
    }

    /**
     * A guideline classification is the output of applying a rule to findings — "severe pneumonia"
     * on a chart, "stage 3a CKD". Recording one as a diagnosis claims more than the chart said.
     */
    @Test
    void keepsAGuidelineClassificationDistinctFromADiagnosis() {
        Map<String, Object> body = problem();
        body.put("display", "Severe pneumonia");
        body.put("category", "GUIDELINE_CLASSIFICATION");
        assertThat(add(body).getCategory()).isEqualTo("GUIDELINE_CLASSIFICATION");
    }

    @Test
    void leavesAnUnstatedCertaintyUnstated() {
        assertThat(add(problem()).getDiagnosticCertainty()).isNull();
    }

    @Test
    void refusesACertaintyOutsideTheVocabulary() {
        Map<String, Object> body = problem();
        body.put("diagnostic_certainty", "PROBABLY");
        assertThatThrownBy(() -> add(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROBABLY");
    }

    @Test
    void refusesACategoryOutsideTheVocabulary() {
        Map<String, Object> body = problem();
        body.put("category", "HUNCH");
        assertThatThrownBy(() -> add(body)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsEvidenceResponsibleServiceAndReviewDate() {
        Map<String, Object> body = problem();
        body.put("evidence", "Clinic BP 168/98 on three occasions");
        body.put("responsible_service", "CARDIOLOGY");
        body.put("review_date", "2026-10-01");
        ProblemEntity p = add(body);
        assertThat(p.getEvidence()).isEqualTo("Clinic BP 168/98 on three occasions");
        assertThat(p.getResponsibleService()).isEqualTo("CARDIOLOGY");
        assertThat(p.getReviewDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    // ── One disease stays one row ───────────────────────────────────────────────

    /**
     * The transition the status vocabulary was widened for. Before it existed, a condition coming
     * back could only be recorded by editing the resolved row or adding a second problem — the
     * second of which is how one disease becomes several entries and a problem list stops being
     * countable.
     */
    @Test
    void aResolvedProblemCanComeBackWithoutBecomingASecondProblem() {
        ProblemEntity p = add(problem());
        UUID id = p.getProblemId();
        when(problemRepository.findById(id)).thenReturn(Optional.of(p));

        ProblemEntity resolved = changeStatus(id, "RESOLVED");
        assertThat(resolved.getClinicalStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getResolvedAt()).isNotNull();

        ProblemEntity recurred = changeStatus(id, "RECURRENCE");
        assertThat(recurred.getProblemId()).isEqualTo(id);
        assertThat(recurred.getClinicalStatus()).isEqualTo("RECURRENCE");
        assertThat(recurred.getLastRecurrenceAt()).isNotNull();
    }

    /** A resolution timestamp left on a problem that is active again misdates the record. */
    @Test
    void clearsTheStaleResolutionWhenAProblemIsNoLongerResolved() {
        ProblemEntity p = add(problem());
        when(problemRepository.findById(p.getProblemId())).thenReturn(Optional.of(p));

        changeStatus(p.getProblemId(), "RESOLVED");
        ProblemEntity recurred = changeStatus(p.getProblemId(), "RELAPSE");

        assertThat(recurred.getResolvedAt()).isNull();
        assertThat(recurred.getLastRecurrenceAt()).isNotNull();
    }

    /** Remission is not recurrence — the condition has not returned, it is being held. */
    @Test
    void remissionDoesNotStampARecurrence() {
        ProblemEntity p = add(problem());
        when(problemRepository.findById(p.getProblemId())).thenReturn(Optional.of(p));

        ProblemEntity remitted = changeStatus(p.getProblemId(), "REMISSION");
        assertThat(remitted.getClinicalStatus()).isEqualTo("REMISSION");
        assertThat(remitted.getLastRecurrenceAt()).isNull();
    }

    @Test
    void refusesAStatusOutsideTheVocabulary() {
        ProblemEntity p = add(problem());
        when(problemRepository.findById(p.getProblemId())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> changeStatus(p.getProblemId(), "CURED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── One disease, one entry, across clinics ──────────────────────────────────

    /**
     * The requirement this implements: a patient seen in the diabetic clinic, the renal clinic and
     * a medical ward acquires the same diagnosis three times unless something looks.
     */
    @Test
    void reportsAProblemAlreadyOnTheListRatherThanSilentlyAddingItTwice() {
        ProblemEntity existing = onList(p -> {
            p.setDisplay("Hypertension");
            p.setCode("I10");
            p.setCodeSystem("ICD-10");
        });

        assertThatThrownBy(() -> add(problem()))
                .isInstanceOf(DuplicateProblemException.class)
                .hasMessageContaining("Hypertension");

        // Reported, not written: the caller has to say what they mean first.
        verify(problemRepository, never()).save(argThat(p -> p.getProblemId() == null));
        assertThat(existing.getClinicalStatus()).isEqualTo("ACTIVE");
    }

    /** Case and spacing are not clinical differences, and most problems here are still free text. */
    @Test
    void matchesOnDisplayWhenTheProblemIsUncoded() {
        onList(p -> {
            p.setDisplay("Type 2 diabetes mellitus");
            p.setCode(null);
            p.setCodeSystem(null);
        });
        Map<String, Object> incoming = problem();
        incoming.put("display", "  type 2   DIABETES mellitus ");
        incoming.remove("code");
        incoming.remove("code_system");

        assertThatThrownBy(() -> add(incoming)).isInstanceOf(DuplicateProblemException.class);
    }

    /**
     * Refusing outright would make a genuine second problem unrecordable — a second primary
     * cancer, the other knee. The clinician says which it is; the service does not guess.
     */
    @Test
    void recordsASeparateProblemWhenTheClinicianSaysItIsDistinct() {
        onList(p -> p.setDisplay("Hypertension"));
        Map<String, Object> incoming = problem();
        incoming.put("duplicate_resolution", "DISTINCT_PROBLEM");

        ProblemEntity created = add(incoming);
        assertThat(created.getDisplay()).isEqualTo("Hypertension");
        assertThat(created.getClinicalStatus()).isEqualTo("ACTIVE");
    }

    /** The other answer: no new row, the disease already listed has come back. */
    @Test
    void movesTheExistingProblemToRecurrenceWhenTheClinicianSaysItIsTheSameOne() {
        ProblemEntity existing = onList(p -> p.setDisplay("Hypertension"));
        when(problemRepository.findById(existing.getProblemId())).thenReturn(Optional.of(existing));

        Map<String, Object> incoming = problem();
        incoming.put("duplicate_resolution", "SAME_PROBLEM_RETURNING");

        ProblemEntity result = add(incoming);
        assertThat(result.getProblemId()).isEqualTo(existing.getProblemId());
        assertThat(result.getClinicalStatus()).isEqualTo("RECURRENCE");
        assertThat(result.getLastRecurrenceAt()).isNotNull();
    }

    /**
     * A second episode of pneumonia two years later is a new problem, not a collision. Treating a
     * resolved row as a duplicate would make the new episode unrecordable without an override.
     */
    @Test
    void aResolvedProblemIsNotADuplicateOfANewOne() {
        onList(p -> {
            p.setDisplay("Community-acquired pneumonia");
            p.setClinicalStatus("RESOLVED");
        });
        Map<String, Object> incoming = problem();
        incoming.put("display", "Community-acquired pneumonia");

        assertThat(add(incoming).getClinicalStatus()).isEqualTo("ACTIVE");
    }

    /** A diagnosis that was excluded must not block someone raising it again on new evidence. */
    @Test
    void aRefutedProblemDoesNotBlockRaisingItAgain() {
        onList(p -> {
            p.setDisplay("Pulmonary embolism");
            p.setDiagnosticCertainty("REFUTED");
        });
        Map<String, Object> incoming = problem();
        incoming.put("display", "Pulmonary embolism");
        incoming.put("diagnostic_certainty", "SUSPECTED");

        assertThat(add(incoming).getDiagnosticCertainty()).isEqualTo("SUSPECTED");
    }

    @Test
    void aDifferentProblemIsNotADuplicate() {
        onList(p -> p.setDisplay("Hypertension"));
        Map<String, Object> incoming = problem();
        incoming.put("display", "Asthma");
        incoming.remove("code");

        assertThat(add(incoming).getDisplay()).isEqualTo("Asthma");
    }

    /** Puts one problem on the patient's list for the duplicate check to find. */
    private ProblemEntity onList(java.util.function.Consumer<ProblemEntity> customise) {
        ProblemEntity existing = new ProblemEntity();
        existing.setProblemId(UUID.randomUUID());
        existing.setTenantId(TENANT_ID);
        existing.setSubjectCpid("CPID-9");
        existing.setDisplay("Hypertension");
        existing.setCode("I10");
        existing.setCodeSystem("ICD-10");
        existing.setCategory("DIAGNOSIS");
        existing.setClinicalStatus("ACTIVE");
        existing.setRecordedBy("clinician-0");
        customise.accept(existing);
        lenient().when(problemRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(TENANT_ID, "CPID-9"))
                .thenReturn(List.of(existing));
        return existing;
    }

    private ProblemEntity changeStatus(UUID problemId, String status) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            return service.changeStatus(problemId, status);
        }
    }
}
