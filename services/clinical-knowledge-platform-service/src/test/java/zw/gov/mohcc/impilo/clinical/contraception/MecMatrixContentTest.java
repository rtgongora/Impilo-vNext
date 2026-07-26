package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The governance gate for the medical eligibility matrix, and its double-entry transcription.
 *
 * <p><b>What the double entry is for, and what it is not.</b> The categories below were transcribed
 * a second time, independently of the JSON and in a different shape — one line per condition, nine
 * initiation/continuation pairs in method order — and the build fails on any disagreement. That
 * catches transcription slips: a misread row, a column shifted by one, a digit typed twice, a value
 * edited in one place and not the other. It does <em>not</em> catch a category that both
 * transcriptions get wrong in the same way, because both came from the same reading of the criteria.
 * Only checking against the vendored primary text catches that, and the primary text is not
 * vendored — which the pack says in its own provenance block, and which is why it ships as an
 * engineering seed.
 *
 * <p>Saying so plainly matters more than the control itself. A double-entry check described as
 * verification, when it is really a consistency check, is the kind of claim that stops anybody
 * looking again.
 */
class MecMatrixContentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MedicalEligibilityService service = new MedicalEligibilityService(objectMapper);

    /** Method order for every row of the second transcription. */
    private static final List<String> METHODS = List.of(
            "COMBINED_ORAL_PILL",
            "PROGESTOGEN_ONLY_PILL",
            "INJECTABLE_DMPA_IM",
            "INJECTABLE_DMPA_SC",
            "INJECTABLE_NET_EN",
            "IMPLANT_LEVONORGESTREL_2ROD",
            "IMPLANT_ETONOGESTREL_1ROD",
            "IUD_COPPER_T380A",
            "IUS_LEVONORGESTREL");

    /**
     * The independent second transcription. Each entry is initiation/continuation, in METHODS order.
     *
     * <pre>
     *                                         COC   POP  DMPAim DMPAsc NETEN ImpLNG ImpETG  CuIUD LNGIUS
     * </pre>
     */
    private static final Map<String, String> SECOND_ENTRY = new LinkedHashMap<>();

    static {
        SECOND_ENTRY.put("AGE_MENARCHE_TO_18",
                "1/1  1/1  2/2  2/2  2/2  1/1  1/1  2/2  2/2");
        SECOND_ENTRY.put("AGE_40_PLUS",
                "2/2  1/1  2/2  2/2  2/2  1/1  1/1  1/1  1/1");
        SECOND_ENTRY.put("SMOKING_35PLUS_LT15",
                "3/3  1/1  1/1  1/1  1/1  1/1  1/1  1/1  1/1");
        SECOND_ENTRY.put("SMOKING_35PLUS_GTE15",
                "4/4  1/1  1/1  1/1  1/1  1/1  1/1  1/1  1/1");
        SECOND_ENTRY.put("HYPERTENSION_MODERATE",
                "3/3  1/1  2/2  2/2  2/2  1/1  1/1  1/1  1/1");
        SECOND_ENTRY.put("HYPERTENSION_SEVERE",
                "4/4  2/2  3/3  3/3  3/3  2/2  2/2  1/1  2/2");
        SECOND_ENTRY.put("MIGRAINE_WITH_AURA",
                "4/4  2/3  2/3  2/3  2/3  2/3  2/3  1/1  2/3");
        SECOND_ENTRY.put("VTE_HISTORY_OR_CURRENT_UNDIFFERENTIATED",
                "4/4  3/3  3/3  3/3  3/3  3/3  3/3  1/1  3/3");
        SECOND_ENTRY.put("BREAST_CANCER_CURRENT",
                "4/4  4/4  4/4  4/4  4/4  4/4  4/4  1/1  4/4");
        SECOND_ENTRY.put("BREAST_CANCER_PAST_5Y",
                "3/3  3/3  3/3  3/3  3/3  3/3  3/3  1/1  3/3");
        SECOND_ENTRY.put("ENZYME_INDUCING_THERAPY",
                "3/3  3/3  1/1  1/1  1/1  2/2  2/2  1/1  1/1");
        SECOND_ENTRY.put("HIV_ON_ART_CLINICALLY_WELL",
                "1/1  1/1  1/1  1/1  1/1  1/1  1/1  1/1  1/1");
    }

    private JsonNode pack() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(MedicalEligibilityService.CONTENT_PATH)) {
            return objectMapper.readTree(stream);
        }
    }

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    // ---------------------------------------------------------------- double entry

    @Test
    @DisplayName("the shipped matrix agrees with an independent second transcription, cell for cell")
    void doubleEntryAgrees() {
        MecMatrix matrix = service.matrix();
        assertThat(matrix).isNotNull();

        for (var entry : SECOND_ENTRY.entrySet()) {
            String condition = entry.getKey();
            String[] pairs = entry.getValue().trim().split("\\s+");
            assertThat(pairs)
                    .as("second transcription of %s has %d columns, expected %d",
                            condition, pairs.length, METHODS.size())
                    .hasSize(METHODS.size());

            for (int i = 0; i < METHODS.size(); i++) {
                String method = METHODS.get(i);
                String[] parts = pairs[i].split("/");
                int expectedInitiation = Integer.parseInt(parts[0]);
                int expectedContinuation = Integer.parseInt(parts[1]);

                MecMatrix.Cell cell = matrix.cell(condition, method);
                assertThat(cell)
                        .as("the shipped matrix has no cell for %s x %s", condition, method)
                        .isNotNull();
                assertThat(cell.initiation())
                        .as("%s x %s initiation", condition, method)
                        .isEqualTo(expectedInitiation);
                assertThat(cell.continuation())
                        .as("%s x %s continuation", condition, method)
                        .isEqualTo(expectedContinuation);
            }
        }
    }

    @Test
    @DisplayName("neither transcription contains a condition the other does not")
    void bothTranscriptionsCoverTheSameConditions() {
        MecMatrix matrix = service.matrix();
        List<String> shipped = matrix.conditions().stream().map(MecMatrix.Condition::code).toList();

        assertThat(shipped)
                .as("a condition in the shipped matrix with no second entry is transcribed once, "
                        + "which is what double entry exists to prevent")
                .containsExactlyInAnyOrderElementsOf(SECOND_ENTRY.keySet());
    }

    // ---------------------------------------------------------------- structural

    @Test
    @DisplayName("methods in the same group carry identical categories for every condition")
    void methodsInAGroupDoNotDrift() throws IOException {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (JsonNode method : pack().path("methods")) {
            groups.computeIfAbsent(method.path("group").asText("UNGROUPED"),
                    key -> new java.util.ArrayList<>()).add(method.path("code").asText());
        }

        MecMatrix matrix = service.matrix();
        for (var group : groups.entrySet()) {
            List<String> members = group.getValue();
            if (members.size() < 2) {
                continue;
            }
            for (MecMatrix.Condition condition : matrix.conditions()) {
                MecMatrix.Cell first = matrix.cell(condition.code(), members.get(0));
                for (String other : members.subList(1, members.size())) {
                    MecMatrix.Cell cell = matrix.cell(condition.code(), other);
                    // The criteria treat these as one method. Writing them out separately is what
                    // lets the engine key on a concrete method code, and it is exactly the
                    // duplication a copy-paste edit diverges.
                    assertThat(cell.initiation())
                            .as("%s: %s and %s are the same method in the criteria but differ",
                                    condition.code(), members.get(0), other)
                            .isEqualTo(first.initiation());
                    assertThat(cell.continuation())
                            .as("%s: %s and %s differ on continuation",
                                    condition.code(), members.get(0), other)
                            .isEqualTo(first.continuation());
                }
            }
        }
    }

    @Test
    @DisplayName("every condition-method pair is classified, so nothing is silently unclassified")
    void theGridIsComplete() {
        assertThat(service.matrix().unclassifiedPairs())
                .as("an unclassified pair is legitimate but must be a hole somebody chose; "
                        + "these were not declared")
                .isEmpty();
    }

    @Test
    @DisplayName("the matrix says what it does not cover, and pregnancy is on that list with a reason")
    void whatIsNotCoveredIsNamed() {
        MecMatrix matrix = service.matrix();

        assertThat(matrix.conditionsOutOfScope())
                .as("a partial matrix that names no gaps reads as a complete one")
                .isNotEmpty();
        assertThat(matrix.conditionsOutOfScope())
                .anyMatch(c -> c.toLowerCase().startsWith("pregnancy"));
        assertThat(matrix.coverageNote()).isNotBlank();
    }

    @Test
    @DisplayName("every condition cites a source and names who must ratify it")
    void everyConditionIsAttributable() throws IOException {
        for (JsonNode condition : pack().path("conditions")) {
            String code = condition.path("code").asText();
            assertThat(condition.path("sourceRefs")).as("%s cites nothing", code).isNotEmpty();
            assertThat(condition.path("requiredInputs")).as("%s declares no inputs", code).isNotEmpty();
            assertThat(condition.path("adaptation").path("approvingAuthority").asText())
                    .as("%s names no approving authority", code).isNotBlank();
        }
        for (JsonNode row : pack().path("cells")) {
            assertThat(row.path("sourceRefs"))
                    .as("the %s row cites nothing", row.path("condition").asText()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("the pack declares whether its primary text is vendored")
    void thePackDeclaresItsSourceVerification() throws IOException {
        JsonNode verification = pack().path("provenance").path("sourceVerification");

        assertThat(verification.has("primaryTextVendored")).isTrue();
        assertThat(verification.path("note").asText())
                .as("the note must say what the double-entry check does NOT catch")
                .contains("does NOT catch");
    }

    // ---------------------------------------------------------------- engine behaviour on real content

    @Test
    @DisplayName("migraine with aura refuses the combined pill and clears the copper device")
    void migraineWithAuraSeparatesTheMethods() {
        var assessment = service.assess(MedicalEligibilityEngine.UsePhase.INITIATION, facts(
                "ageYears", 30,
                "migraineWithAura", "PRESENT",
                "smokingStatus", "NEVER",
                "systolicBp", 118, "diastolicBp", 74,
                "knownThrombosis", "ABSENT",
                "breastCancerStatus", "NONE",
                "onEnzymeInducingArvOrTb", "NO",
                "hivStatus", "NEGATIVE"), null);

        var byMethod = new HashMap<String, MedicalEligibilityEngine.Eligibility>();
        assessment.methods().forEach(m -> byMethod.put(m.methodCode(), m));

        assertThat(byMethod.get("COMBINED_ORAL_PILL").recommendation())
                .isEqualTo(MedicalEligibilityEngine.Recommendation.DO_NOT_USE);
        assertThat(byMethod.get("IUD_COPPER_T380A").recommendation())
                .isEqualTo(MedicalEligibilityEngine.Recommendation.USE);
        assertThat(byMethod.get("COMBINED_ORAL_PILL").drivingConditions())
                .extracting(MedicalEligibilityEngine.DrivingCondition::conditionCode)
                .contains("MIGRAINE_WITH_AURA");
    }

    @Test
    @DisplayName("continuation is not initiation — aura appearing on a method already in use reads differently")
    void continuationCategoriesDiffer() {
        Map<String, Object> f = facts(
                "ageYears", 30, "migraineWithAura", "PRESENT", "smokingStatus", "NEVER",
                "systolicBp", 118, "diastolicBp", 74, "knownThrombosis", "ABSENT",
                "breastCancerStatus", "NONE", "onEnzymeInducingArvOrTb", "NO",
                "hivStatus", "NEGATIVE");

        var starting = service.assess(MedicalEligibilityEngine.UsePhase.INITIATION, f,
                List.of("IMPLANT_ETONOGESTREL_1ROD"));
        var continuing = service.assess(MedicalEligibilityEngine.UsePhase.CONTINUATION, f,
                List.of("IMPLANT_ETONOGESTREL_1ROD"));

        assertThat(starting.methods().get(0).category()).isEqualTo(2);
        assertThat(continuing.methods().get(0).category()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unasked question that could reach category 4 is never answered USE")
    void anUnaskedQuestionIsNeverAGreenLight() {
        var assessment = service.assess(MedicalEligibilityEngine.UsePhase.INITIATION,
                facts("ageYears", 38), null);

        var coc = assessment.methods().stream()
                .filter(m -> "COMBINED_ORAL_PILL".equals(m.methodCode())).findFirst().orElseThrow();

        assertThat(coc.recommendation())
                .isEqualTo(MedicalEligibilityEngine.Recommendation.CANNOT_DETERMINE);
        assertThat(coc.category())
                .as("a suppressed category, because showing '1' beside CANNOT_DETERMINE is read as 1")
                .isNull();
        assertThat(coc.missingInputs()).contains("smokingStatus");
    }

    @Test
    @DisplayName("the out-of-scope list travels on every assessment, not only when something is wrong")
    void theGapIsAlwaysVisible() {
        var assessment = service.assess(MedicalEligibilityEngine.UsePhase.INITIATION, facts(
                "ageYears", 25, "migraineWithAura", "ABSENT", "smokingStatus", "NEVER",
                "systolicBp", 110, "diastolicBp", 70, "knownThrombosis", "ABSENT",
                "breastCancerStatus", "NONE", "onEnzymeInducingArvOrTb", "NO",
                "hivStatus", "NEGATIVE"), null);

        assertThat(assessment.conditionsOutOfScope()).isNotEmpty();
        assertThat(assessment.note()).isNotBlank();
        assertThat(assessment.approvalStatus()).isEqualTo("ENGINEERING_SEED");
    }

    @Test
    @DisplayName("a missing matrix reports unavailable, never that every method is safe")
    void aMissingMatrixIsNeverSilence() {
        MecMatrix absent = service.matrix("clinical/does-not-exist.json");
        assertThat(absent).isNull();

        var assessment = MedicalEligibilityEngine.assess(absent,
                MedicalEligibilityEngine.UsePhase.INITIATION, facts(), null);
        assertThat(assessment.methods()).isEmpty();
        assertThat(assessment.contentProblems()).isNotEmpty();
        assertThat(assessment.note()).contains("not a statement that any method is safe");
    }
}
