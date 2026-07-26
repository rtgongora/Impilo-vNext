package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Medical eligibility resolution.
 *
 * <p>Uses a small hand-built matrix rather than the shipped content, so the properties under test
 * are the engine's and not a particular transcription's. The shipped matrix has its own fixture
 * gate.
 */
class MedicalEligibilityEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode logic(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Two methods, three conditions, deliberately including a hole. */
    private static MecMatrix matrix() {
        return MecMatrix.of("test.mec", "test-1.0.0", "ENGINEERING_SEED", "TEST", "test fixture",
                "Covers 3 conditions of an eventual many.",
                List.of(new MecMatrix.Method("CHC_COC", "Combined oral contraceptive", "CHC"),
                        new MecMatrix.Method("POP", "Progestogen-only pill", "POP"),
                        new MecMatrix.Method("CU_IUD", "Copper intrauterine device", "IUD")),
                List.of(
                        new MecMatrix.Condition("HTN_SEVERE", "Hypertension 160/100 or above",
                                logic("""
                                        {"any": [
                                          {"input": "vitals.systolicBp", "op": "gte", "value": 160},
                                          {"input": "vitals.diastolicBp", "op": "gte", "value": 100}]}"""),
                                List.of("vitals.systolicBp", "vitals.diastolicBp"), List.of("test")),
                        new MecMatrix.Condition("MIGRAINE_AURA", "Migraine with aura",
                                logic("{\"finding\": \"migraineWithAura\", \"present\": true}"),
                                List.of("migraineWithAura"), List.of("test")),
                        new MecMatrix.Condition("BREASTFEEDING_UNDER_6W", "Breastfeeding, under 6 weeks postpartum",
                                logic("{\"finding\": \"breastfeedingUnder6Weeks\", \"present\": true}"),
                                List.of("breastfeedingUnder6Weeks"), List.of("test"))),
                List.of("Anything not listed above — this fixture is not the criteria."),
                List.of(
                        new MecMatrix.Cell("HTN_SEVERE", "CHC_COC", 4, 4,
                                "Combined hormonal contraceptives further raise blood pressure.", List.of("test")),
                        new MecMatrix.Cell("HTN_SEVERE", "POP", 1, 1, null, List.of("test")),
                        new MecMatrix.Cell("HTN_SEVERE", "CU_IUD", 1, 1, null, List.of("test")),
                        new MecMatrix.Cell("MIGRAINE_AURA", "CHC_COC", 4, 4, null, List.of("test")),
                        // Initiation and continuation differ, which is the point of the split.
                        new MecMatrix.Cell("MIGRAINE_AURA", "POP", 2, 3, null, List.of("test")),
                        new MecMatrix.Cell("MIGRAINE_AURA", "CU_IUD", 1, 1, null, List.of("test")),
                        new MecMatrix.Cell("BREASTFEEDING_UNDER_6W", "CHC_COC", 3, 3, null, List.of("test")),
                        new MecMatrix.Cell("BREASTFEEDING_UNDER_6W", "POP", 1, 1, null, List.of("test"))
                        // deliberately no BREASTFEEDING_UNDER_6W x CU_IUD cell
                ));
    }

    private static MedicalEligibilityEngine.Eligibility forMethod(
            MedicalEligibilityEngine.Assessment assessment, String code) {
        return assessment.methods().stream()
                .filter(m -> m.methodCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void aGreenLightIsNeverIssuedFromAnUnaskedQuestion() {
        // THE safety property. Every assessed condition puts the progestogen-only pill at category 1,
        // but migraine with aura was never asked and would place it at 2 on initiation and 3 on
        // continuation. A confident "use it" here is the failure this engine exists to prevent.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.CONTINUATION,
                Map.of("vitals", Map.of("systolicBp", 168, "diastolicBp", 104)), null);

        var pop = forMethod(assessment, "POP");
        assertEquals(MedicalEligibilityEngine.Recommendation.CANNOT_DETERMINE, pop.recommendation());
        assertNull(pop.category(), "a category shown beside CANNOT_DETERMINE reads as an answer");
        assertTrue(pop.unresolvedConditions().contains("MIGRAINE_AURA"));
        assertTrue(pop.missingInputs().contains("migraineWithAura"));
        assertTrue(pop.rationale().contains("Ask before recommending"));
    }

    @Test
    void anUnaskedQuestionThatCannotChangeTheAnswerIsNotReported() {
        // The other half of the same rule. If every result were provisional forever, clinicians
        // would learn to ignore the flag and it would stop protecting anyone.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION,
                Map.of("vitals", Map.of("systolicBp", 168, "diastolicBp", 104)), null);

        var chc = forMethod(assessment, "CHC_COC");
        assertEquals(MedicalEligibilityEngine.Recommendation.DO_NOT_USE, chc.recommendation());
        assertEquals(4, chc.category());
        assertFalse(chc.provisional(),
                "migraine is unasked but cannot make a 4 worse, so it must not muddy the answer");
    }

    @Test
    void resolutionIsMaxOverConditions_notFirstMatch() {
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION,
                Map.of("vitals", Map.of("systolicBp", 168, "diastolicBp", 104),
                       "migraineWithAura", "YES",
                       "breastfeedingUnder6Weeks", "NO"), null);

        var chc = forMethod(assessment, "CHC_COC");
        assertEquals(4, chc.category());
        // Both restricting conditions are named, not just the first one found.
        assertEquals(2, chc.drivingConditions().size());
    }

    @Test
    void initiationAndContinuationAreAssessedSeparately() {
        // For several conditions stopping is more harmful than continuing, so a woman already using
        // a method must not be judged on the initiation column.
        Map<String, Object> facts = Map.of("migraineWithAura", "YES",
                "vitals", Map.of("systolicBp", 110, "diastolicBp", 70),
                "breastfeedingUnder6Weeks", "NO");

        var starting = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION, facts, List.of("POP"));
        var continuing = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.CONTINUATION, facts, List.of("POP"));

        assertEquals(MedicalEligibilityEngine.Recommendation.USE_WITH_FOLLOW_UP,
                forMethod(starting, "POP").recommendation());
        assertEquals(MedicalEligibilityEngine.Recommendation.SPECIALIST_JUDGEMENT_REQUIRED,
                forMethod(continuing, "POP").recommendation());
    }

    @Test
    void categoryThreeIsNeverReducedToANumber() {
        // MEC 3 means "not usually recommended unless other more appropriate methods are not
        // available or acceptable" — a judgement about the alternatives, not a threshold. Rendered
        // as a bare category a UI shows it as a slightly worse 2.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION,
                Map.of("breastfeedingUnder6Weeks", "YES",
                       "migraineWithAura", "NO",
                       "vitals", Map.of("systolicBp", 110, "diastolicBp", 70)), List.of("CHC_COC"));

        var chc = forMethod(assessment, "CHC_COC");
        assertEquals(MedicalEligibilityEngine.Recommendation.SPECIALIST_JUDGEMENT_REQUIRED,
                chc.recommendation());
        assertTrue(chc.rationale().contains("judgement about what else is available"));
    }

    @Test
    void aPairTheCriteriaDoNotCoverIsNotTreatedAsNoRestriction() {
        // The matrix has no breastfeeding x copper IUD cell. Silence there is a hole in the content,
        // not a clearance — and a partial transcription is only shippable because this holds.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION,
                Map.of("breastfeedingUnder6Weeks", "YES",
                       "migraineWithAura", "NO",
                       "vitals", Map.of("systolicBp", 110, "diastolicBp", 70)), List.of("CU_IUD"));

        var iud = forMethod(assessment, "CU_IUD");
        assertEquals(MedicalEligibilityEngine.Recommendation.CANNOT_DETERMINE, iud.recommendation());
        assertTrue(iud.provisional());
        assertTrue(iud.rationale().contains("do not cover"));
    }

    @Test
    void everyAssessmentCarriesWhatTheMatrixDoesNotCover() {
        // A partial matrix must not read as a complete one. The out-of-scope declaration travels on
        // every response so a clinician can see her patient's condition may simply not be in it.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION, Map.of(), null);

        assertFalse(assessment.conditionsOutOfScope().isEmpty());
        assertNotNull(assessment.note());
        assertEquals("ENGINEERING_SEED", assessment.approvalStatus());
    }

    @Test
    void noMatrixMeansNoAnswer_notAnEmptyAllClear() {
        var assessment = MedicalEligibilityEngine.assess(null,
                MedicalEligibilityEngine.UsePhase.INITIATION, Map.of(), null);

        assertTrue(assessment.methods().isEmpty());
        assertTrue(assessment.note().contains("not a statement that any method is safe"));
    }

    @Test
    void aWomanWithNoRecordedConditionsStillGetsAnHonestAnswer() {
        // Nothing asked at all: every condition is unresolved, so anything they could raise must
        // surface rather than every method reading as category 1.
        var assessment = MedicalEligibilityEngine.assess(matrix(),
                MedicalEligibilityEngine.UsePhase.INITIATION, Map.of(), null);

        var chc = forMethod(assessment, "CHC_COC");
        assertEquals(MedicalEligibilityEngine.Recommendation.CANNOT_DETERMINE, chc.recommendation());
        assertEquals(3, assessment.conditionsNotAssessed().size());
    }
}
