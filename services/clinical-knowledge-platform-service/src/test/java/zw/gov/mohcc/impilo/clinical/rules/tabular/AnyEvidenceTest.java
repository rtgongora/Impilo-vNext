package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code any} combinator reports every input that qualified, not just the first.
 *
 * <p>The verdict still short-circuits — one true is decisive. The evidence does not, because the
 * evidence is what a downstream layer uses to decide what to do.
 */
class AnyEvidenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PredicateEvaluator.Result evaluate(String json, Map<String, Object> facts) {
        try {
            return PredicateEvaluator.evaluate(MAPPER.readTree(json), facts);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyQualifyingInputIsReported_notOnlyTheFirst() {
        // The live case that exposed this: a ten-day-old fired on poor feeding, and the alert never
        // mentioned the qualifying temperature of 35.0 because evaluation stopped at the first true.
        var result = evaluate("""
                {"any": [
                   {"finding": "feeding", "present": true},
                   {"input": "vitals.temperature", "op": "lt", "value": 35.5}]}
                """, Map.of("feeding", "YES", "vitals", Map.of("temperature", 35.0)));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
        assertThat(result.usedInputs()).contains("feeding", "vitals.temperature");
    }

    @Test
    void twoSimultaneousEmergenciesBothAppearInTheEvidence() {
        // Why this stops being cosmetic in the maternal domain: triggeringFindings selects which
        // emergency response opens. A woman who is convulsing AND bleeding must open both the
        // eclampsia pathway and the haemorrhage pathway; reporting only the first would leave the
        // second invisible behind a complete-looking assessment.
        var result = evaluate("""
                {"any": [
                   {"finding": "convulsing", "present": true},
                   {"finding": "heavyBleeding", "present": true}]}
                """, Map.of("convulsing", "YES", "heavyBleeding", "YES"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
        assertThat(result.usedInputs()).contains("convulsing", "heavyBleeding");
    }

    @Test
    void theVerdictStillShortCircuits_aTrueBeatsAnUnknown() {
        // Unchanged semantics: one definite yes settles the question even if something else in the
        // list was never asked. Only the evidence collection changed.
        var result = evaluate("""
                {"any": [
                   {"finding": "convulsing", "present": true},
                   {"finding": "neverAsked", "present": true}]}
                """, Map.of("convulsing", "YES"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.TRUE);
    }

    @Test
    void anUnknownStillPreventsAFalse() {
        var result = evaluate("""
                {"any": [
                   {"finding": "convulsing", "present": true},
                   {"finding": "neverAsked", "present": true}]}
                """, Map.of("convulsing", "NO"));

        assertThat(result.outcome()).isEqualTo(PredicateEvaluator.Outcome.UNKNOWN);
    }
}
