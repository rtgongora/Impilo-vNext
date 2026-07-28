package zw.gov.mohcc.impilo.clinical.programme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The governance gate for the HIV and TB programme content.
 *
 * <p>Every rule ships fixtures, and they run against the real {@link ProgrammeGuidanceService} — the
 * whole pack, including the {@code appliesWhen} gate — so a content edit that changes clinical
 * behaviour fails the build rather than reaching a clinic. Running the fixture through the service
 * (not the raw predicate) is deliberate: several of these rules are scoped by applicability
 * (treatment phase, adherence completed), and a logic-only check would pass a fixture the rule would
 * never actually fire on for that patient.</p>
 */
class HivTbProgrammeRuleContentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleContentLoader loader = new RuleContentLoader(objectMapper);
    private final ProgrammeGuidanceService service = new ProgrammeGuidanceService(loader,
            new zw.gov.mohcc.impilo.clinical.multimorbidity.MedicationFactDeriver(
                    new zw.gov.mohcc.impilo.clinical.multimorbidity.MedicineClassifier(objectMapper)));

    private static final List<String> PACKS = List.of(
            ProgrammeGuidanceService.HIV_CONTENT_PATH,
            ProgrammeGuidanceService.TB_CONTENT_PATH,
            ProgrammeGuidanceService.HIV_TESTING_PATH,
            ProgrammeGuidanceService.TB_SCREENING_PATH,
            ProgrammeGuidanceService.TB_DIAGNOSIS_PATH,
            ProgrammeGuidanceService.TPT_PATH,
            ProgrammeGuidanceService.PREP_PATH,
            ProgrammeGuidanceService.PMTCT_PATH,
            ProgrammeGuidanceService.CVD_RISK_PATH,
            ProgrammeGuidanceService.DEPRESCRIBING_PATH,
            ProgrammeGuidanceService.PROCEDURE_INDICATION_PATH,
            ProgrammeGuidanceService.ICOPE_PATH,
            ProgrammeGuidanceService.MHGAP_PATH,
            ProgrammeGuidanceService.AMS_PATH,
            ProgrammeGuidanceService.PALLIATIVE_PATH,
            ProgrammeGuidanceService.ONCOLOGY_PATH);

    @TestFactory
    List<DynamicTest> everyRulesOwnFixturesBehaveAsDescribed() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String pack : PACKS) {
            List<TabularRule> rules = loader.load(pack);
            assertThat(rules).as("pack %s loaded no rules", pack).isNotEmpty();
            for (TabularRule rule : rules) {
                assertThat(rule.testCases())
                        .as("rule %s ships no fixtures, so a change to it could not be caught", rule.code())
                        .isNotEmpty();
                for (TabularRule.TestCase testCase : rule.testCases()) {
                    tests.add(DynamicTest.dynamicTest(rule.code() + " — " + testCase.name(), () -> {
                        var assessment = service.evaluatePack(pack, testCase.facts());
                        assertThat(assessment.contentProblems())
                                .as("%s produced a content error: %s", rule.code(), assessment.contentProblems())
                                .isEmpty();
                        assertThat(assessment.fired(rule.code()))
                                .as("%s — %s", rule.code(), testCase.name())
                                .isEqualTo(testCase.expectFires());
                    }));
                }
            }
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyRuleStatesWhatToDoAndWhereItCameFrom() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String pack : PACKS) {
            for (TabularRule rule : loader.load(pack)) {
                tests.add(DynamicTest.dynamicTest(pack + " / " + rule.code(), () -> {
                    assertThat(rule.requiredAction())
                            .as("%s must tell a clinician what to do", rule.code()).isNotBlank();
                    assertThat(rule.sourceRefs())
                            .as("%s must cite its source", rule.code()).isNotEmpty();
                    assertThat(rule.requiredInputs())
                            .as("%s must declare the facts it reads", rule.code()).isNotEmpty();
                    assertThat(rule.provenance())
                            .as("%s must carry an adaptation block", rule.code()).isNotNull();
                    assertThat(rule.provenance().adaptationType())
                            .as("%s must say what was done to the source text", rule.code()).isNotBlank();
                }));
            }
        }
        return tests;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/hiv-programme-rules.json",
            "clinical/tb-programme-rules.json",
            "clinical/hiv-testing-rules.json",
            "clinical/tb-screening-rules.json",
            "clinical/tb-diagnosis-rules.json",
            "clinical/tpt-rules.json",
            "clinical/prep-rules.json",
            "clinical/pmtct-rules.json",
            "clinical/cvd-risk-rules.json",
            "clinical/deprescribing-rules.json",
            "clinical/procedure-indication-rules.json",
            "clinical/icope-rules.json",
            "clinical/mhgap-rules.json",
            "clinical/antimicrobial-stewardship-rules.json",
            "clinical/palliative-rules.json",
            "clinical/oncology-early-diagnosis-rules.json"})
    void thePackDeclaresItsPrimaryTextIsNotYetVendored(String pack) throws IOException {
        JsonNode verification = readPack(pack).path("provenance").path("sourceVerification");
        // Not an assertion that it IS vendored — it is not. An assertion that the pack says so where a
        // reader will see it, because this content was authored against published WHO text rather than
        // extracted from a pinned artefact, and that departure (and the ratification block it creates)
        // must not be silent. Same rule the RMNP SPR/MEC packs hold themselves to.
        assertThat(verification.has("primaryTextVendored")).isTrue();
        assertThat(verification.path("primaryTextVendored").asBoolean(true)).isFalse();
        assertThat(verification.path("blocksRatification").asBoolean(false)).isTrue();
        assertThat(verification.path("note").asText()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "clinical/hiv-programme-rules.json",
            "clinical/tb-programme-rules.json",
            "clinical/hiv-testing-rules.json",
            "clinical/tb-screening-rules.json",
            "clinical/tb-diagnosis-rules.json",
            "clinical/tpt-rules.json",
            "clinical/prep-rules.json",
            "clinical/pmtct-rules.json",
            "clinical/cvd-risk-rules.json",
            "clinical/deprescribing-rules.json",
            "clinical/procedure-indication-rules.json",
            "clinical/icope-rules.json",
            "clinical/mhgap-rules.json",
            "clinical/antimicrobial-stewardship-rules.json",
            "clinical/palliative-rules.json",
            "clinical/oncology-early-diagnosis-rules.json"})
    void thePackShipsAsEngineeringSeed(String pack) throws IOException {
        // No unratified pack may claim APPROVED — that is the one status that would let it drive care.
        assertThat(readPack(pack).path("approvalStatus").asText()).isEqualTo("ENGINEERING_SEED");
    }

    private JsonNode readPack(String pack) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(pack)) {
            assertThat(stream).as("pack %s missing from classpath", pack).isNotNull();
            return objectMapper.readTree(stream);
        }
    }
}
