package zw.gov.mohcc.impilo.clinical.maternal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationContentLoader;
import zw.gov.mohcc.impilo.clinical.classification.ClassificationEngine;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Antenatal classification — anaemia in pregnancy graded on the existing IMNCI engine.
 *
 * <p>The two properties worth pinning: the trimester threshold is applied by band (10.6 g/dL is
 * anaemia in the third trimester and not in the second), and a missing haemoglobin is
 * INDETERMINATE, never a quiet "no anaemia" — the reassuring row is unreachable until every more
 * severe row has been excluded.
 */
class AntenatalClassificationTest {

    private final AntenatalClassificationService service =
            new AntenatalClassificationService(new ClassificationContentLoader(new ObjectMapper()));

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private ClassificationEngine.Outcome anaemia(Map<String, Object> f) {
        return service.evaluate(f).outcomes().stream()
                .filter(o -> "ANAEMIA".equals(o.axis())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Hb 6.2 is severe anaemia — urgent referral")
    void severe() {
        var o = anaemia(facts("haemoglobin", 6.2, "gestationalAgeWeeks", 30));
        assertThat(o.classificationCode()).isEqualTo("SEVERE_ANAEMIA");
        assertThat(o.urgentReferral()).isTrue();
        assertThat(service.evaluate(facts("haemoglobin", 6.2, "gestationalAgeWeeks", 30))
                .urgentReferral()).isTrue();
    }

    @Test
    @DisplayName("Hb 8.5 is moderate anaemia")
    void moderate() {
        assertThat(anaemia(facts("haemoglobin", 8.5, "gestationalAgeWeeks", 20)).classificationCode())
                .isEqualTo("MODERATE_ANAEMIA");
    }

    @Test
    @DisplayName("the trimester threshold is applied by band: 10.6 is anaemia at 30 weeks, not at 20")
    void trimesterThresholdByBand() {
        assertThat(anaemia(facts("haemoglobin", 10.6, "gestationalAgeWeeks", 30)).classificationCode())
                .as("third trimester threshold is 11.0, so 10.6 is mild anaemia")
                .isEqualTo("MILD_ANAEMIA");
        assertThat(anaemia(facts("haemoglobin", 10.6, "gestationalAgeWeeks", 20)).classificationCode())
                .as("second trimester threshold is 10.5, so 10.6 is not anaemia")
                .isEqualTo("NO_ANAEMIA");
    }

    @Test
    @DisplayName("Hb 12.0 is not anaemia in any trimester")
    void notAnaemic() {
        assertThat(anaemia(facts("haemoglobin", 12.0, "gestationalAgeWeeks", 30)).classificationCode())
                .isEqualTo("NO_ANAEMIA");
    }

    @Test
    @DisplayName("no haemoglobin cannot be classified — INDETERMINATE, never a quiet no-anaemia")
    void missingHaemoglobinIsIndeterminate() {
        var o = anaemia(facts("gestationalAgeWeeks", 30));
        assertThat(o.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(o.classificationCode()).isNotEqualTo("NO_ANAEMIA");
        assertThat(service.evaluate(facts("gestationalAgeWeeks", 30)).incomplete()).isTrue();
    }

    @Test
    @DisplayName("no gestational age still classifies severe and moderate (Hb-only), and only mild needs the band")
    void severeDoesNotNeedGestation() {
        // Severe/moderate are pure Hb thresholds. Only the mild/none boundary needs the trimester.
        assertThat(anaemia(facts("haemoglobin", 6.2)).classificationCode())
                .isEqualTo("SEVERE_ANAEMIA");
        // With Hb 10.6 and no gestation, the mild band cannot resolve, so it is INDETERMINATE
        // rather than defaulting to no-anaemia — a boundary case must not resolve reassuringly on a
        // missing scale.
        var borderline = anaemia(facts("haemoglobin", 10.6));
        assertThat(borderline.classificationCode()).isNotEqualTo("NO_ANAEMIA");
    }

    @Test
    @DisplayName("the content fixtures all pass through the loaded pack")
    void contentFixturesPass() {
        var pack = new ClassificationContentLoader(new ObjectMapper())
                .load(AntenatalClassificationService.CONTENT_PATH);
        assertThat(pack.tables()).isNotEmpty();
        for (var table : pack.tables()) {
            assertThat(table.testCases()).as("%s ships no fixtures", table.tableId()).isNotEmpty();
            for (var fixture : table.testCases()) {
                var result = ClassificationEngine.classify(table, null, fixture.facts());
                if (fixture.expectClassification() != null) {
                    assertThat(result.classificationCode())
                            .as("%s: %s", table.tableId(), fixture.name())
                            .isEqualTo(fixture.expectClassification());
                } else {
                    assertThat(result.status())
                            .as("%s: %s expects no classification", table.tableId(), fixture.name())
                            .isNotEqualTo(ClassificationEngine.Status.CLASSIFIED);
                }
            }
        }
    }

    private ClassificationEngine.Outcome birthLevel(Map<String, Object> f) {
        return service.evaluate(f).outcomes().stream()
                .filter(o -> "BIRTH_CARE_LEVEL".equals(o.axis())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a previous caesarean requires comprehensive EmONC")
    void previousCaesareanNeedsCemonc() {
        assertThat(birthLevel(facts("previousCaesarean", "PRESENT")).classificationCode())
                .isEqualTo("CEMONC_REQUIRED");
    }

    @Test
    @DisplayName("all risk factors excluded is BEmONC-sufficient")
    void allExcludedIsBasicSufficient() {
        assertThat(birthLevel(facts(
                "previousCaesarean", "ABSENT", "currentObstetricComplication", "ABSENT",
                "malpresentationAtTerm", "ABSENT", "multiplePregnancy", "ABSENT",
                "significantMedicalComorbidity", "ABSENT")).classificationCode())
                .isEqualTo("BEMONC_SUFFICIENT");
    }

    @Test
    @DisplayName("nothing asked cannot conclude basic-sufficient — the level is INDETERMINATE")
    void unaskedRiskIsNotBasicSufficient() {
        var o = birthLevel(facts());
        // The safety property: "basic is enough" is only issued once every comprehensive-EmONC
        // indication has been excluded. Asked nothing, the level is indeterminate — never a green
        // light to give birth somewhere without a caesarean.
        assertThat(o.status()).isEqualTo(ClassificationEngine.Status.INDETERMINATE);
        assertThat(o.classificationCode()).isNotEqualTo("BEMONC_SUFFICIENT");
    }

    @Test
    @DisplayName("one excluded factor but the rest unasked is still not basic-sufficient")
    void partialExclusionIsStillIndeterminate() {
        var o = birthLevel(facts("previousCaesarean", "ABSENT"));
        assertThat(o.classificationCode()).isNotEqualTo("BEMONC_SUFFICIENT");
    }

    @Test
    @DisplayName("missing content is reported incomplete, never as normal")
    void missingContentIsNeverSilence() {
        var assessment = service.evaluate("clinical/does-not-exist.json", facts("haemoglobin", 6.0));
        assertThat(assessment.incomplete()).isTrue();
        assertThat(assessment.outcomes()).isEmpty();
        assertThat(assessment.note()).contains("not a statement that everything is normal");
    }
}
