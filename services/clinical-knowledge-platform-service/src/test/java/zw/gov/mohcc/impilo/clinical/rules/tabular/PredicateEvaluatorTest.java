package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PredicateEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aFindingThatWasNeverAssessedIsUnknownNotAbsent() {
        // The single most important property here. A danger sign nobody looked for must not
        // read as a danger sign that is not there.
        var result = evaluate("""
                {"finding": "convulsions", "present": true}
                """, Map.of("feeding", "POOR"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).contains("convulsions");
    }

    @Test
    void anExplicitlyNotAssessedValueIsAlsoUnknown() {
        var result = evaluate("""
                {"finding": "convulsions", "present": true}
                """, Map.of("convulsions", "NOT_ASSESSED"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).contains("convulsions");
    }

    @Test
    void aFindingRecordedAsAbsentIsFalseNotUnknown() {
        var result = evaluate("""
                {"finding": "convulsions", "present": true}
                """, Map.of("convulsions", "ABSENT"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.FALSE);
        assertThat(result.missingInputs()).isEmpty();
    }

    @Test
    void aConjunctionWithOneDefiniteFalseIsFalseEvenWithUnansweredQuestions() {
        // Nothing the missing answer could say would make the conjunction true, so the result
        // is decisive and does not need to be hedged.
        var result = evaluate("""
                {"all": [
                  {"finding": "convulsions", "present": true},
                  {"finding": "lethargy", "present": true}
                ]}
                """, Map.of("lethargy", "ABSENT"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.FALSE);
    }

    @Test
    void aConjunctionThatDependsOnAnUnansweredQuestionIsUnknown() {
        var result = evaluate("""
                {"all": [
                  {"finding": "convulsions", "present": true},
                  {"finding": "lethargy", "present": true}
                ]}
                """, Map.of("lethargy", "PRESENT"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).containsExactly("convulsions");
    }

    @Test
    void aDisjunctionWithOneDefiniteTrueIsTrueEvenWithUnansweredQuestions() {
        var result = evaluate("""
                {"any": [
                  {"finding": "convulsions", "present": true},
                  {"finding": "lethargy", "present": true}
                ]}
                """, Map.of("lethargy", "PRESENT"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
    }

    @Test
    void aDisjunctionOfUnansweredQuestionsIsUnknownRatherThanFalse() {
        var result = evaluate("""
                {"any": [
                  {"finding": "convulsions", "present": true},
                  {"finding": "lethargy", "present": true}
                ]}
                """, Map.of());

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).containsExactly("convulsions", "lethargy");
    }

    @Test
    void negationOfAnUnknownStaysUnknown() {
        var result = evaluate("""
                {"not": {"finding": "convulsions", "present": true}}
                """, Map.of());

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
    }

    @Test
    void numericComparisonsReadNestedVitals() {
        Map<String, Object> facts = Map.of("vitals", Map.of("heartRate", 195));

        assertThat(evaluate("""
                {"input": "vitals.heartRate", "op": "gte", "value": 180}
                """, facts).holds()).isTrue();
        assertThat(evaluate("""
                {"input": "vitals.heartRate", "op": "lt", "value": 180}
                """, facts).holds()).isFalse();
    }

    @Test
    void betweenBoundsAreInclusive() {
        assertThat(evaluate("""
                {"input": "ageDays", "op": "between", "min": 0, "max": 59}
                """, Map.of("ageDays", 59)).holds()).isTrue();
        assertThat(evaluate("""
                {"input": "ageDays", "op": "between", "min": 0, "max": 59}
                """, Map.of("ageDays", 60)).holds()).isFalse();
    }

    @Test
    void codedSetMembershipIsCaseInsensitive() {
        assertThat(evaluate("""
                {"input": "feeding", "op": "in", "values": ["POOR", "NONE"]}
                """, Map.of("feeding", "poor")).holds()).isTrue();
        assertThat(evaluate("""
                {"input": "feeding", "op": "not_in", "values": ["POOR", "NONE"]}
                """, Map.of("feeding", "WELL")).holds()).isTrue();
    }

    @Test
    void ageBandedThresholdsPickTheBandForTheChildsAge() {
        String fastBreathing = """
                {"input": "vitals.respiratoryRate", "bands": [
                  {"ageMinDays": 0,   "ageMaxDays": 59,   "gte": 60},
                  {"ageMinDays": 60,  "ageMaxDays": 364,  "gte": 50},
                  {"ageMinDays": 365, "ageMaxDays": 1824, "gte": 40}
                ]}
                """;
        // 52 breaths a minute is fast breathing for a six-month-old and not for a newborn.
        assertThat(evaluate(fastBreathing,
                Map.of("ageDays", 180, "vitals", Map.of("respiratoryRate", 52))).holds()).isTrue();
        assertThat(evaluate(fastBreathing,
                Map.of("ageDays", 20, "vitals", Map.of("respiratoryRate", 52))).holds()).isFalse();
        // And for a three-year-old the threshold is lower again.
        assertThat(evaluate(fastBreathing,
                Map.of("ageDays", 1000, "vitals", Map.of("respiratoryRate", 42))).holds()).isTrue();
    }

    @Test
    void anAgeBandedThresholdWithoutAnAgeIsUnknown() {
        var result = evaluate("""
                {"input": "vitals.respiratoryRate", "bands": [{"ageMinDays": 0, "ageMaxDays": 59, "gte": 60}]}
                """, Map.of("vitals", Map.of("respiratoryRate", 70)));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).contains("ageDays");
    }

    @Test
    void anAgeOutsideEveryBandIsUnknownAndReportedAsAContentGap() {
        // The content simply does not speak to this patient. Answering "false" would assert a
        // clinical finding the content never made.
        var result = evaluate("""
                {"input": "vitals.respiratoryRate", "bands": [{"ageMinDays": 0, "ageMaxDays": 59, "gte": 60}]}
                """, Map.of("ageDays", 900, "vitals", Map.of("respiratoryRate", 70)));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        // The message names the scale as well as the value: "no band covers 900" is unreadable
        // without knowing whether 900 is an age in days or a gestation in days.
        assertThat(result.contentErrors()).anyMatch(e -> e.contains("No band covers 900 ageDays"));
    }

    @Test
    void bandsCanBeScoredAgainstAScaleOtherThanAge() {
        // Anaemia in pregnancy is defined against gestation, not age: the same haemoglobin is
        // normal in the second trimester and abnormal at term.
        var result = evaluate("""
                {"input": "haemoglobinGdl", "bandKey": "gestationalAgeDays", "bands": [
                   {"bandMin": 0,   "bandMax": 195, "lt": 11.0},
                   {"bandMin": 196, "bandMax": 294, "lt": 10.5}]}
                """, Map.of("gestationalAgeDays", 220, "haemoglobinGdl", 10.2));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
    }

    @Test
    void aMaternalBandNeverReadsThePatientsAgeByAccident() {
        // The shortcut this guards against is passing gestational age in as "ageDays". Facts are a
        // flat map shared by every rule in a request, so a fact named ageDays holding 220 would read
        // as a seven-month-old to the dosing engine. A rule banded on gestation must report the
        // gestation missing, not silently score itself against the woman's own age.
        var result = evaluate("""
                {"input": "haemoglobinGdl", "bandKey": "gestationalAgeDays", "bands": [
                   {"bandMin": 0, "bandMax": 294, "lt": 11.0}]}
                """, Map.of("ageDays", 9000, "haemoglobinGdl", 10.2));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).contains("gestationalAgeDays");
        assertThat(result.missingInputs()).doesNotContain("ageDays");
    }

    @Test
    void existingAgeBandContentIsUnaffectedByTheNewSpelling() {
        // Every shipped paediatric rule uses ageMinDays/ageMaxDays with no bandKey. Those must keep
        // resolving against ageDays exactly as before, or this change would have rewritten the
        // meaning of content that was live-proven on the estate.
        var result = evaluate("""
                {"input": "vitals.respiratoryRate", "bands": [
                   {"ageMinDays": 0,  "ageMaxDays": 59,  "gte": 60},
                   {"ageMinDays": 60, "ageMaxDays": 364, "gte": 50}]}
                """, Map.of("ageDays", 30, "vitals", Map.of("respiratoryRate", 62)));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
    }

    @Test
    void anUnsupportedOperatorIsAContentErrorRatherThanASilentFalse() {
        var result = evaluate("""
                {"input": "vitals.heartRate", "op": "approximately", "value": 180}
                """, Map.of("vitals", Map.of("heartRate", 180)));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.hasContentErrors()).isTrue();
    }

    @Test
    void aMalformedPredicateIsRejectedRatherThanGuessed() {
        assertThat(evaluate("{\"whenever\": true}", Map.of()).hasContentErrors()).isTrue();
        assertThat(evaluate("{\"all\": []}", Map.of()).hasContentErrors()).isTrue();
    }

    @Test
    void presenceAndAbsenceOperatorsTestWhetherAValueWasRecordedAtAll() {
        assertThat(evaluate("""
                {"input": "muacCm", "op": "present"}
                """, Map.of("muacCm", 11.0)).holds()).isTrue();
        assertThat(evaluate("""
                {"input": "muacCm", "op": "absent"}
                """, Map.of()).holds()).isTrue();
    }

    @Test
    void aNonNumericValueInANumericComparisonIsUnknownNotFalse() {
        var result = evaluate("""
                {"input": "vitals.heartRate", "op": "gte", "value": 180}
                """, Map.of("vitals", Map.of("heartRate", "not recorded")));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
        assertThat(result.missingInputs()).contains("vitals.heartRate");
    }

    @Test
    void theInputsThatDroveTheDecisionAreReportedBack() {
        var result = evaluate("""
                {"all": [
                  {"finding": "lethargy", "present": true},
                  {"input": "vitals.heartRate", "op": "gte", "value": 180}
                ]}
                """, Map.of("lethargy", "PRESENT", "vitals", Map.of("heartRate", 190)));

        assertThat(result.holds()).isTrue();
        assertThat(result.usedInputs()).containsExactly("lethargy", "vitals.heartRate");
    }

    private PredicateEvaluator.Result evaluate(String json, Map<String, Object> facts) {
        try {
            JsonNode node = mapper.readTree(json);
            return PredicateEvaluator.evaluate(node, new LinkedHashMap<>(facts));
        } catch (Exception e) {
            throw new IllegalStateException("bad test fixture", e);
        }
    }
}
