package zw.gov.mohcc.impilo.clinical.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImnciClassificationServiceTest {

    private ImnciClassificationService service;
    private ClassificationContentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ClassificationContentLoader(new ObjectMapper());
        service = new ImnciClassificationService(loader);
    }

    private static Map<String, Object> facts(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private ClassificationEngine.Outcome outcome(ImnciClassificationService.Assessment a, String tableId) {
        return a.classifications().stream()
                .filter(o -> o.tableId().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + tableId));
    }

    // --- the fixtures that ship with the content -------------------------------------------

    /**
     * Every table's own fixtures, executed against the real engine. This is the gate that stops a
     * content edit from silently changing which children are classified as severely ill: the
     * clinical content and its expected behaviour travel together and are checked on every build.
     */
    @TestFactory
    @DisplayName("content fixtures")
    Stream<DynamicTest> contentFixturesPass() {
        ClassificationContentLoader.Pack pack = loader.load(ImnciClassificationService.CONTENT_PATH);
        assertThat(pack.tables()).as("classification pack must load").isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (ClassificationTable table : pack.tables()) {
            assertThat(table.testCases())
                    .as("table %s ships no fixtures — every clinical table must carry its own", table.tableId())
                    .isNotEmpty();
            for (ClassificationTable.TestCase fixture : table.testCases()) {
                tests.add(DynamicTest.dynamicTest(table.tableId() + ": " + fixture.name(), () -> {
                    Integer ageDays = fixture.facts().get("ageDays") instanceof Number n ? n.intValue() : null;
                    ClassificationEngine.Outcome result = ClassificationEngine.classify(
                            table, ageDays, ImnciClassificationService.flatten(fixture.facts()));

                    assertThat(result.status().name())
                            .as("%s — status", fixture.name())
                            .isEqualTo(fixture.expectStatus());
                    if (fixture.expectClassification() != null) {
                        assertThat(result.classificationCode())
                                .as("%s — classification", fixture.name())
                                .isEqualTo(fixture.expectClassification());
                    }
                    assertThat(result.provisional())
                            .as("%s — provisional", fixture.name())
                            .isEqualTo(fixture.expectProvisional());
                }));
            }
        }
        return tests.stream();
    }

    // --- the safety property this engine exists for ------------------------------------------

    @Test
    void anUnexaminedChestCannotBeClassifiedAsACold() {
        // The single most important behaviour here. A coughing child whose chest was never
        // examined is not a child without chest indrawing. Falling through to the green row would
        // convert a skipped examination into "send home with a safe cough remedy".
        var a = service.evaluate(420, facts("cough", "PRESENT", "vitals", Map.of("respiratoryRate", 30)));
        var pneumonia = outcome(a, "IMNCI_PNEUMONIA");

        assertThat(pneumonia.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(pneumonia.classificationCode()).isNull();
        assertThat(pneumonia.unresolvedTiers()).contains("SEVERE_PNEUMONIA_OR_VERY_SEVERE_DISEASE");
        assertThat(pneumonia.missingInputs()).contains("chestIndrawing");
        assertThat(pneumonia.rationale()).contains("never sought");
    }

    @Test
    void aMatchedRowIsMarkedProvisionalWhenASevererRowWasNeverExcluded() {
        // Fast breathing is present, so "pneumonia" matches — but nobody looked for chest
        // indrawing, so severe pneumonia was not excluded. The answer is a floor, not a verdict.
        var a = service.evaluate(420, facts(
                "cough", "PRESENT",
                "vitals", Map.of("respiratoryRate", 48)));
        var pneumonia = outcome(a, "IMNCI_PNEUMONIA");

        assertThat(pneumonia.classificationCode()).isEqualTo("PNEUMONIA");
        assertThat(pneumonia.provisional()).isTrue();
        assertThat(pneumonia.actionable()).isFalse();
        assertThat(pneumonia.rationale()).contains("least severe classification");
    }

    @Test
    void anIncompleteAssessmentIsNeverADischarge() {
        var a = service.evaluate(420, facts("cough", "PRESENT"));

        assertThat(a.incomplete()).isTrue();
        assertThat(a.disposition()).isEqualTo("COMPLETE_ASSESSMENT_BEFORE_DISPOSITION");
        assertThat(a.note()).contains("least severe");
    }

    @Test
    void aFullyAssessedWellChildIsCleanlyClassifiedAndTreatable() {
        var a = service.evaluate(420, facts(
                "cough", "ABSENT", "difficultBreathing", "ABSENT",
                "diarrhoea", "ABSENT",
                "feverByHistory", "ABSENT", "feelsHot", "ABSENT",
                "generalisedRash", "ABSENT", "measlesWithinThreeMonths", "ABSENT",
                "earProblem", "ABSENT",
                "muacCm", 14.5, "bilateralPittingOedema", "ABSENT", "appetiteTest", "PASSED",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT",
                "palmarPallor", "NONE",
                "vitals", Map.of("temperature", 36.8)));

        assertThat(a.incomplete()).isFalse();
        assertThat(a.urgentReferralRequired()).isFalse();
        assertThat(a.disposition()).isEqualTo("TREAT_AT_FACILITY");
        assertThat(outcome(a, "IMNCI_ANAEMIA").classificationCode()).isEqualTo("NO_ANAEMIA");
        assertThat(outcome(a, "IMNCI_ACUTE_MALNUTRITION").classificationCode())
                .isEqualTo("NO_ACUTE_MALNUTRITION");
    }

    // --- integration is the point --------------------------------------------------------------

    @Test
    void aChildBroughtForCoughIsStillAssessedForMalnutritionAndAnaemia() {
        // The reason IMNCI is integrated: what kills the child is often not what brought them in.
        var a = service.evaluate(420, facts(
                "cough", "PRESENT", "chestIndrawing", "ABSENT", "stridorAtRest", "ABSENT",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT",
                "vitals", Map.of("respiratoryRate", 30),
                "muacCm", 10.9, "bilateralPittingOedema", "ABSENT", "appetiteTest", "PASSED",
                "palmarPallor", "SEVERE"));

        assertThat(outcome(a, "IMNCI_PNEUMONIA").classificationCode()).isEqualTo("COUGH_OR_COLD");
        // The cough is trivial. The child is severely malnourished and severely anaemic.
        assertThat(outcome(a, "IMNCI_ACUTE_MALNUTRITION").classificationCode())
                .isEqualTo("UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION");
        assertThat(outcome(a, "IMNCI_ANAEMIA").classificationCode()).isEqualTo("SEVERE_ANAEMIA");
        assertThat(a.urgentReferralRequired()).isTrue();
        assertThat(a.disposition()).isEqualTo("REFER_URGENTLY");
    }

    @Test
    void theTreatmentPlanLeadsWithTheUrgentActions() {
        var a = service.evaluate(420, facts(
                "cough", "PRESENT", "chestIndrawing", "PRESENT",
                "diarrhoea", "PRESENT", "bloodInStool", "PRESENT",
                "lethargicOrUnconscious", "ABSENT", "sunkenEyes", "ABSENT",
                "drinkingPattern", "DRINKS_NORMALLY", "skinPinch", "NORMAL",
                "restlessIrritable", "ABSENT",
                "muacCm", 14.0, "bilateralPittingOedema", "ABSENT", "appetiteTest", "PASSED",
                "palmarPallor", "NONE"));

        assertThat(a.treatmentPlan().get(0)).contains("antibiotic");
        assertThat(a.treatmentPlan()).contains("Urgent referral");
        // The dysentery treatment is still in the plan, below the urgent actions.
        assertThat(a.treatmentPlan()).anyMatch(t -> t.contains("Ciprofloxacin"));
    }

    @Test
    void malariaIsNeverClassifiedWithoutAParasitologicalTest() {
        // Presumptive treatment on fever alone is contrary to current WHO policy and would drive
        // antimalarial use in a country where transmission is focal.
        var a = service.evaluate(420, facts(
                "feelsHot", "PRESENT",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT",
                "stiffNeck", "ABSENT", "bulgingFontanelle", "ABSENT"));
        var fever = outcome(a, "IMNCI_FEVER_MALARIA");

        assertThat(fever.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(fever.missingInputs()).contains("malariaTestResult");
    }

    // --- age routing ----------------------------------------------------------------------------

    @Test
    void aYoungInfantIsRoutedAwayFromTheseTablesRatherThanClassifiedByThem() {
        var a = service.evaluate(30, facts("cough", "PRESENT"));

        assertThat(a.applicable()).isFalse();
        assertThat(a.classifications()).isEmpty();
        assertThat(a.note()).contains("young infant");
        assertThat(a.disposition()).isEqualTo("COMPLETE_ASSESSMENT_BEFORE_DISPOSITION");
    }

    @Test
    void withoutAnAgeNoTableIsApplied() {
        var a = service.evaluate(null, facts("cough", "PRESENT"));

        assertThat(a.applicable()).isFalse();
        assertThat(a.note()).contains("Age is unknown");
    }

    @Test
    void theFastBreathingThresholdChangesAtOneYear() {
        Map<String, Object> base = facts(
                "cough", "PRESENT", "chestIndrawing", "ABSENT", "stridorAtRest", "ABSENT",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT",
                "vitals", Map.of("respiratoryRate", 45));

        // 45 breaths a minute: normal for a 6-month-old, pneumonia for a 3-year-old.
        assertThat(outcome(service.evaluate(180, base), "IMNCI_PNEUMONIA").classificationCode())
                .isEqualTo("COUGH_OR_COLD");
        assertThat(outcome(service.evaluate(1100, base), "IMNCI_PNEUMONIA").classificationCode())
                .isEqualTo("PNEUMONIA");
    }

    // --- provenance -------------------------------------------------------------------------------

    @Test
    void everyAssessmentCarriesItsContentVersionAndApprovalStatus() {
        var a = service.evaluate(420, facts("cough", "ABSENT"));

        assertThat(a.contentVersion()).isEqualTo("engineering-seed-1.0.0");
        assertThat(a.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(a.contentSource())
                .contains("Integrated Management of Newborn and Childhood Illness")
                .contains("NOT a ratified national protocol");
    }

    // --- the optional-criterion waiver ------------------------------------------------------------

    @Test
    void aClinicWithoutAHeightBoardStillGetsAUsableNutritionClassification() {
        // Weight-for-height is an independent route to severe acute malnutrition, so strictly it is
        // unresolved when height was never measured. But most rural clinics assess on MUAC and
        // oedema alone, which WHO endorses, and flagging every one of their nutrition
        // classifications as provisional would make the flag noise. The content marks
        // weight-for-height optional; the waiver is recorded rather than hidden.
        var a = service.evaluate(420, facts(
                "muacCm", 12.0, "bilateralPittingOedema", "ABSENT", "appetiteTest", "PASSED",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT"));
        var nutrition = outcome(a, "IMNCI_ACUTE_MALNUTRITION");

        assertThat(nutrition.classificationCode()).isEqualTo("MODERATE_ACUTE_MALNUTRITION");
        assertThat(nutrition.provisional()).isFalse();
        // The gap is still on the record.
        assertThat(nutrition.waivedCriteria()).contains("weightForHeightZ");
        assertThat(a.unavailableCriteria()).contains("weightForHeightZ");
    }

    @Test
    void anAvailableWeightForHeightStillClassifiesSevereMalnutrition() {
        // The waiver must not disable the criterion when it IS measured.
        var a = service.evaluate(420, facts(
                "muacCm", 13.0, "bilateralPittingOedema", "ABSENT", "weightForHeightZ", -3.4,
                "appetiteTest", "PASSED",
                "unableToDrinkOrBreastfeed", "ABSENT", "vomitsEverything", "ABSENT",
                "convulsions", "ABSENT", "lethargicOrUnconscious", "ABSENT"));
        var nutrition = outcome(a, "IMNCI_ACUTE_MALNUTRITION");

        assertThat(nutrition.classificationCode()).isEqualTo("UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION");
        assertThat(nutrition.waivedCriteria()).isEmpty();
    }

    @Test
    void theWaiverDoesNotLeakIntoNonOptionalCriteria() {
        // MUAC is not optional. A child never measured at all is still indeterminate.
        var a = service.evaluate(420, facts("palmarPallor", "NONE"));
        var nutrition = outcome(a, "IMNCI_ACUTE_MALNUTRITION");

        assertThat(nutrition.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(nutrition.missingInputs()).contains("muacCm");
    }

    @Test
    void classificationIsNotRecordedAsDiagnosis() {
        // Guards the doctrine, not just the code: if a diagnosis field is ever added to this
        // result, it will be asserting something no clinician said.
        var fields = List.of(ImnciClassificationService.Assessment.class.getRecordComponents())
                .stream().map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(fields).doesNotContain("diagnosis", "workingDiagnosis", "icd10");
    }
}
