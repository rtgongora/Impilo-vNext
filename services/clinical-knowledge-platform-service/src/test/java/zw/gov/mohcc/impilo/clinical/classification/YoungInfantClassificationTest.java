package zw.gov.mohcc.impilo.clinical.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sick young infant, birth up to 2 months.
 *
 * <p>The fixtures are newborns as they actually present. A young infant's signs of serious illness
 * are few, non-specific, and different from an older child's: this is the age group where a baby
 * who is merely "not feeding well" is septic until proven otherwise, and where a low temperature
 * is as ominous as a high one.</p>
 */
class YoungInfantClassificationTest {

    private ImnciClassificationService service;
    private ClassificationContentLoader loader;
    private DangerSignEvaluationService dangerSigns;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        loader = new ClassificationContentLoader(mapper);
        service = new ImnciClassificationService(loader);
        dangerSigns = new DangerSignEvaluationService(new RuleContentLoader(mapper));
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
                .filter(o -> o.tableId().equals(tableId)).findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + tableId));
    }

    /** A well infant, fully assessed — the baseline the sick fixtures deviate from. */
    private static Map<String, Object> wellInfant() {
        return facts(
                "feeding", "GOOD", "convulsions", "ABSENT", "movesOnlyWhenStimulated", "ABSENT",
                "severeChestIndrawing", "ABSENT", "bulgingFontanelle", "ABSENT",
                "umbilicus", "NORMAL", "skinPustules", "ABSENT",
                "jaundice", "ABSENT",
                "diarrhoea", "ABSENT",
                "attachment", "GOOD", "sucklingEffective", "EFFECTIVE", "feedsPer24Hours", 10,
                "receivesOtherFoodsOrDrinks", "ABSENT", "weightForAgeZ", -0.3, "oralThrush", "ABSENT",
                "vitals", Map.of("temperature", 36.8, "respiratoryRate", 44));
    }

    @TestFactory
    @DisplayName("young-infant content fixtures")
    Stream<DynamicTest> contentFixturesPass() {
        ClassificationContentLoader.Pack pack =
                loader.load(ImnciClassificationService.YOUNG_INFANT_CONTENT_PATH);
        assertThat(pack.tables()).as("young-infant pack must load").isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (ClassificationTable table : pack.tables()) {
            assertThat(table.testCases())
                    .as("table %s ships no fixtures", table.tableId()).isNotEmpty();
            for (ClassificationTable.TestCase fixture : table.testCases()) {
                tests.add(DynamicTest.dynamicTest(table.tableId() + ": " + fixture.name(), () -> {
                    Integer ageDays = fixture.facts().get("ageDays") instanceof Number n ? n.intValue() : null;
                    ClassificationEngine.Outcome result = ClassificationEngine.classify(
                            table, ageDays, ImnciClassificationService.flatten(fixture.facts()));
                    assertThat(result.status().name()).as("%s — status", fixture.name())
                            .isEqualTo(fixture.expectStatus());
                    if (fixture.expectClassification() != null) {
                        assertThat(result.classificationCode()).as("%s — classification", fixture.name())
                                .isEqualTo(fixture.expectClassification());
                    }
                }));
            }
        }
        return tests.stream();
    }

    // --- the invariant that keeps the two CDS layers honest -------------------------------------

    /**
     * The danger-sign layer and the classification layer must never disagree about the same baby.
     *
     * <p>They are separate layers by design — one interrupts, the other decides management — but
     * they read the same findings, and if their content drifts apart a clinician gets a critical
     * alert alongside a classification saying serious infection is unlikely. That contradiction
     * would destroy trust in both. This test pins them together across a spread of presentations.</p>
     */
    @Test
    void theDangerSignAlertAndTheClassificationNeverContradictEachOther() {
        List<Map<String, Object>> presentations = List.of(
                merge(wellInfant(), facts("feeding", "POOR")),
                merge(wellInfant(), facts("vitals", Map.of("temperature", 35.0, "respiratoryRate", 44))),
                merge(wellInfant(), facts("vitals", Map.of("temperature", 38.2, "respiratoryRate", 44))),
                merge(wellInfant(), facts("vitals", Map.of("temperature", 36.8, "respiratoryRate", 64))),
                merge(wellInfant(), facts("severeChestIndrawing", "PRESENT")),
                merge(wellInfant(), facts("convulsions", "PRESENT")),
                merge(wellInfant(), facts("movesOnlyWhenStimulated", "PRESENT")),
                wellInfant());

        for (Map<String, Object> presentation : presentations) {
            var classification = outcome(service.evaluate(10, presentation), "IMNCI_YI_BACTERIAL_INFECTION");
            var alerts = dangerSigns.evaluate(10, presentation);

            boolean psbiAlertFired = alerts.alerts().stream()
                    .anyMatch(al -> "PSBI_YOUNG_INFANT".equals(al.code()));
            boolean classifiedAsPsbi =
                    "POSSIBLE_SERIOUS_BACTERIAL_INFECTION".equals(classification.classificationCode());

            assertThat(classifiedAsPsbi)
                    .as("danger-sign alert and classification disagree for %s", presentation)
                    .isEqualTo(psbiAlertFired);
        }
    }

    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overrides) {
        Map<String, Object> m = new LinkedHashMap<>(base);
        m.putAll(overrides);
        return m;
    }

    // --- the presentations that matter ----------------------------------------------------------

    @Test
    void aNewbornWhoIsMerelyNotFeedingWellIsSeriouslyIll() {
        // The whole point of the young-infant chart. In an older child, poor feeding is one danger
        // sign among many; at ten days old it is on its own enough to mean urgent referral.
        var a = service.evaluate(10, merge(wellInfant(), facts("feeding", "POOR")));

        assertThat(outcome(a, "IMNCI_YI_BACTERIAL_INFECTION").classificationCode())
                .isEqualTo("POSSIBLE_SERIOUS_BACTERIAL_INFECTION");
        assertThat(a.urgentReferralRequired()).isTrue();
        assertThat(a.disposition()).isEqualTo("REFER_URGENTLY");
        assertThat(a.treatmentPlan().get(0)).contains("injectable");
    }

    @Test
    void aColdBabyIsAsUrgentAsAFeverishOne() {
        var cold = service.evaluate(10, merge(wellInfant(),
                facts("vitals", Map.of("temperature", 35.0, "respiratoryRate", 44))));
        var hot = service.evaluate(10, merge(wellInfant(),
                facts("vitals", Map.of("temperature", 38.2, "respiratoryRate", 44))));

        assertThat(outcome(cold, "IMNCI_YI_BACTERIAL_INFECTION").colour()).isEqualTo("PINK");
        assertThat(outcome(hot, "IMNCI_YI_BACTERIAL_INFECTION").colour()).isEqualTo("PINK");
    }

    @Test
    void theYoungInfantBreathingThresholdIsSixtyNotForty() {
        // 50 breaths a minute is pneumonia in a 6-month-old and normal in a 10-day-old. Applying
        // the child threshold here would refer most healthy newborns.
        var a = service.evaluate(10, merge(wellInfant(),
                facts("vitals", Map.of("temperature", 36.8, "respiratoryRate", 50))));
        assertThat(outcome(a, "IMNCI_YI_BACTERIAL_INFECTION").classificationCode())
                .isEqualTo("SEVERE_DISEASE_OR_LOCAL_INFECTION_UNLIKELY");

        var fast = service.evaluate(10, merge(wellInfant(),
                facts("vitals", Map.of("temperature", 36.8, "respiratoryRate", 62))));
        assertThat(outcome(fast, "IMNCI_YI_BACTERIAL_INFECTION").classificationCode())
                .isEqualTo("POSSIBLE_SERIOUS_BACTERIAL_INFECTION");
    }

    @Test
    void umbilicalRednessSpreadingToTheSkinIsSystemicNotLocal() {
        var local = service.evaluate(10, merge(wellInfant(), facts("umbilicus", "RED_OR_DRAINING_PUS")));
        assertThat(outcome(local, "IMNCI_YI_BACTERIAL_INFECTION").classificationCode())
                .isEqualTo("LOCAL_BACTERIAL_INFECTION");

        var spreading = service.evaluate(10, merge(wellInfant(),
                facts("umbilicus", "REDNESS_EXTENDING_TO_SKIN")));
        assertThat(outcome(spreading, "IMNCI_YI_BACTERIAL_INFECTION").classificationCode())
                .isEqualTo("POSSIBLE_SERIOUS_BACTERIAL_INFECTION");
    }

    @Test
    void jaundiceOnDayOneAndJaundiceOnDayFifteenAreBothSevereForDifferentReasons() {
        var earlyOnset = service.evaluate(0, merge(wellInfant(),
                facts("jaundice", "PRESENT", "jaundiceOnsetAgeHours", 10, "palmsSolesYellow", "ABSENT")));
        var prolonged = service.evaluate(15, merge(wellInfant(),
                facts("jaundice", "PRESENT", "jaundiceOnsetAgeHours", 96, "palmsSolesYellow", "ABSENT")));

        assertThat(outcome(earlyOnset, "IMNCI_YI_JAUNDICE").classificationCode()).isEqualTo("SEVERE_JAUNDICE");
        assertThat(outcome(prolonged, "IMNCI_YI_JAUNDICE").classificationCode()).isEqualTo("SEVERE_JAUNDICE");

        // Day-three jaundice with normal palms is the common physiological picture.
        var physiological = service.evaluate(3, merge(wellInfant(),
                facts("jaundice", "PRESENT", "jaundiceOnsetAgeHours", 60, "palmsSolesYellow", "ABSENT")));
        assertThat(outcome(physiological, "IMNCI_YI_JAUNDICE").classificationCode()).isEqualTo("JAUNDICE");
    }

    @Test
    void anInfantNeverWeighedCannotBeClearedOfAFeedingProblem() {
        Map<String, Object> unweighed = new LinkedHashMap<>(wellInfant());
        unweighed.remove("weightForAgeZ");

        var feeding = outcome(service.evaluate(20, unweighed), "IMNCI_YI_FEEDING");

        // Weight is not marked optional here, unlike weight-for-height in the child tables. A scale
        // is basic equipment, a young infant cannot be managed without a weight, and no medicine
        // can be dosed without one.
        assertThat(feeding.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(feeding.missingInputs()).contains("weightForAgeZ");
        assertThat(feeding.waivedCriteria()).isEmpty();
    }

    @Test
    void aWellNewbornIsCleanlyClearedWithNothingOutstanding() {
        var a = service.evaluate(10, wellInfant());

        assertThat(a.incomplete()).isFalse();
        assertThat(a.urgentReferralRequired()).isFalse();
        assertThat(a.disposition()).isEqualTo("TREAT_AT_FACILITY");
        assertThat(a.outstandingAssessments()).isEmpty();
    }

    @Test
    void anUnassessedNewbornIsNeverCleared() {
        var a = service.evaluate(10, facts());

        assertThat(a.incomplete()).isTrue();
        assertThat(a.disposition()).isEqualTo("COMPLETE_ASSESSMENT_BEFORE_DISPOSITION");
        assertThat(outcome(a, "IMNCI_YI_BACTERIAL_INFECTION").status())
                .isEqualTo(ClassificationEngine.Status.INDETERMINATE);
    }

    @Test
    void theYoungInfantPackCitesItsOwnProvenance() {
        var a = service.evaluate(10, wellInfant());

        assertThat(a.pathway()).isEqualTo("SICK_YOUNG_INFANT_0_TO_2_MONTHS");
        assertThat(a.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(a.contentSource()).contains("sick young infant");
    }
}
