package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * Structured history, and specifically the three defects in the orphaned V32 schema that this
 * implementation refuses to reproduce.
 *
 * <p>V32 was the contract the UI was built against, so its column shapes are followed — but
 * following it faithfully would have copied a defaulted clinical judgement, a mandatory score that
 * forces fabrication, and third-party personal data.</p>
 */
@ExtendWith(MockitoExtension.class)
class StructuredHistoryServiceTest {

    @Mock private SocialHistoryRepository socialRepository;
    @Mock private FamilyHistoryMemberRepository familyMemberRepository;
    @Mock private FamilyHistoryConditionRepository familyConditionRepository;
    @Mock private FunctionalAssessmentRepository functionalRepository;
    @Mock private PastProcedureRepository procedureRepository;
    @Mock private AdvanceDirectiveRepository directiveRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private StructuredHistoryService service;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StructuredHistoryService(socialRepository, familyMemberRepository,
                familyConditionRepository, functionalRepository, procedureRepository,
                directiveRepository, accessGuard);
        lenient().when(socialRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(familyMemberRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(functionalRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(procedureRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(directiveRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private <T> T asClinician(Supplier<T> action) {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(new TrustContext(
                    TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT", null,
                    UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
            return action.get();
        }
    }

    private static Map<String, Object> base() {
        Map<String, Object> b = new HashMap<>();
        b.put("subject_cpid", "CPID-9");
        return b;
    }

    // ── V32 defect 1: a defaulted clinical judgement ────────────────────────────

    /**
     * V32 declared {@code risk_level NOT NULL DEFAULT 'Low'}. That records an assessment nobody
     * made, and of the available defaults "low" is the one that stops a reader looking further.
     */
    @Test
    void anUnassessedSocialRiskStaysUnassessedRatherThanBecomingLow() {
        Map<String, Object> b = base();
        b.put("category", "tobacco");
        b.put("status", "Never smoked");

        SocialHistoryEntity e = asClinician(() -> service.recordSocialHistory(b));
        assertThat(e.getRiskLevel()).isNull();
        assertThat(e.getCategory()).isEqualTo("TOBACCO");
    }

    @Test
    void recordsAStatedRiskLevelAndRefusesOneOutsideTheVocabulary() {
        Map<String, Object> b = base();
        b.put("category", "ALCOHOL");
        b.put("status", "Hazardous use");
        b.put("risk_level", "high");
        assertThat(asClinician(() -> service.recordSocialHistory(b)).getRiskLevel()).isEqualTo("HIGH");

        Map<String, Object> bad = base();
        bad.put("category", "ALCOHOL");
        bad.put("status", "x");
        bad.put("risk_level", "SEVERE");
        assertThatThrownBy(() -> asClinician(() -> service.recordSocialHistory(bad)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── V32 defect 2: a mandatory score that forces fabrication ─────────────────

    /**
     * V32 made {@code total_score NOT NULL}, so an assessment that was attempted and could not be
     * completed — a patient too unwell to perform the tasks — had to invent a number. A fabricated
     * zero on a Barthel index reads as total dependence.
     */
    @Test
    void anAssessmentThatCouldNotBeScoredRecordsWhyInsteadOfAZero() {
        Map<String, Object> b = base();
        b.put("assessment_type", "BARTHEL");
        b.put("score_absent_reason", "PATIENT_TOO_UNWELL");

        FunctionalAssessmentEntity e = asClinician(() -> service.recordFunctionalAssessment(b));
        assertThat(e.getTotalScore()).isNull();
        assertThat(e.getScoreAbsentReason()).isEqualTo("PATIENT_TOO_UNWELL");
    }

    @Test
    void refusesAnAssessmentThatCarriesNeitherAScoreNorAReason() {
        Map<String, Object> b = base();
        b.put("assessment_type", "BARTHEL");
        assertThatThrownBy(() -> asClinician(() -> service.recordFunctionalAssessment(b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score_absent_reason");
    }

    @Test
    void refusesAnAssessmentThatClaimsBothAScoreAndAReasonItCouldNotBeScored() {
        Map<String, Object> b = base();
        b.put("assessment_type", "BARTHEL");
        b.put("total_score", 60);
        b.put("score_absent_reason", "PATIENT_TOO_UNWELL");
        assertThatThrownBy(() -> asClinician(() -> service.recordFunctionalAssessment(b)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAScoreOutsideTheInstrumentsRange() {
        Map<String, Object> b = base();
        b.put("assessment_type", "BARTHEL");
        b.put("total_score", 140);
        b.put("max_score", 100);
        assertThatThrownBy(() -> asClinician(() -> service.recordFunctionalAssessment(b)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── V32 defect 3: third-party personal data ─────────────────────────────────

    /**
     * V32 stored a name for each relative. A relative is a third party who has not consented and
     * has no record here, and the relationship is what carries the clinical meaning. The
     * distinguisher solves the case a name was actually solving — two brothers — without recording
     * anyone's identity.
     */
    @Test
    void recordsARelativeByRelationshipRatherThanByName() {
        Map<String, Object> b = base();
        b.put("relationship", "sibling");
        b.put("distinguisher", "older brother");
        b.put("age", 54);

        FamilyHistoryMemberEntity e = asClinician(() -> service.recordFamilyMember(b));
        assertThat(e.getRelationship()).isEqualTo("SIBLING");
        assertThat(e.getDistinguisher()).isEqualTo("older brother");
    }

    /** Unknown is not alive — a family history nobody completed is not a family of living relatives. */
    @Test
    void anUnstatedDeceasedFlagStaysUnknown() {
        Map<String, Object> b = base();
        b.put("relationship", "MOTHER");
        assertThat(asClinician(() -> service.recordFamilyMember(b)).getDeceased()).isNull();
    }

    @Test
    void refusesACauseOfDeathForARelativeNotRecordedAsDeceased() {
        Map<String, Object> b = base();
        b.put("relationship", "FATHER");
        b.put("cause_of_death", "Myocardial infarction");
        assertThatThrownBy(() -> asClinician(() -> service.recordFamilyMember(b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not recorded as deceased");
    }

    // ── Procedural history remembers how sure the date is ───────────────────────

    /**
     * "About ten years ago" is a real answer. Storing it as an exact date would let a later reader
     * compute intervals from a number the patient never gave.
     */
    @Test
    void keepsHowPreciseARememberedProcedureDateIs() {
        Map<String, Object> b = base();
        b.put("name", "Appendicectomy");
        b.put("procedure_date", "2016-01-01");
        b.put("date_precision", "approximate");
        b.put("status", "REPORTED_BY_PATIENT");

        PastProcedureEntity e = asClinician(() -> service.recordPastProcedure(b));
        assertThat(e.getDatePrecision()).isEqualTo("APPROXIMATE");
        assertThat(e.getStatus()).isEqualTo("REPORTED_BY_PATIENT");
    }

    @Test
    void recordsAnAdvanceDirectiveAgainstItsSourceDocument() {
        UUID documentId = UUID.randomUUID();
        Map<String, Object> b = base();
        b.put("directive_type", "DNACPR");
        b.put("document_id", documentId.toString());

        AdvanceDirectiveEntity e = asClinician(() -> service.recordAdvanceDirective(b));
        assertThat(e.getDirectiveType()).isEqualTo("DNACPR");
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        assertThat(e.getDocumentId()).isEqualTo(documentId);
    }

    @Test
    void everyWriteIsAttributedToTheAuthenticatedActor() {
        Map<String, Object> b = base();
        b.put("category", "HOUSING");
        b.put("status", "Stable");
        b.put("recorded_by", "someone-else");
        assertThat(asClinician(() -> service.recordSocialHistory(b)).getRecordedBy())
                .isEqualTo("clinician-1");
    }
}
