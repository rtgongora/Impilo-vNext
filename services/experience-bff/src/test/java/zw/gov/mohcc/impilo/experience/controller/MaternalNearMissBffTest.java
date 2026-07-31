package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clinician near-miss surface, and specifically the form-21 translation — the join that decides
 * whether a filled form ever becomes an identified near-miss.
 *
 * <p>Every test here is about one distinction: a criterion answered "no" versus a criterion nobody
 * answered. The first is a clinical negative. The second is unresolved. A translation that treats them
 * alike records a woman who nearly died as a normal birth, and improves the near-miss-to-death ratio
 * by removing the case with the most to learn from.
 */
class MaternalNearMissBffTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Captures the observation map the BFF actually sends upstream. */
    private class StubCkp extends ClinicalKnowledgePlatformClient {
        Map<String, Object> lastObservations;
        Map<String, Object> lastIndicatorRequest;
        RuntimeException toThrow;

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode nearMissClassify(Map<String, Object> body) {
            if (toThrow != null) {
                throw toThrow;
            }
            lastObservations = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode data = mapper.createObjectNode();
            data.put("classification_code", "NEAR_MISS_NOT_MET");
            data.put("is_near_miss", false);
            return data;
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode nearMissIndicators(Map<String, Object> body) {
            if (toThrow != null) {
                throw toThrow;
            }
            lastIndicatorRequest = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode data = mapper.createObjectNode();
            data.put("mortality_index", 0.25);
            return data;
        }
    }

    private static MaternalNearMissController controller(StubCkp ckp) {
        return new MaternalNearMissController(ckp);
    }

    private static MaternalNearMissController.FormClassifyRequest form(Map<String, Object> answers) {
        return new MaternalNearMissController.FormClassifyRequest(
                "impilo.maternal.nearmiss.assessment.v1", answers);
    }

    private static Map<String, Object> answers(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> meta(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("meta");
    }

    // ── Form-21 translation ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PRESENT and ABSENT become true and false, and the linkId prefix is stripped")
    void codedAnswersTranslate() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "PRESENT",
                "nearMiss.stroke", "ABSENT",
                "nearMiss.creatinineUmolL", 340)), "req-1", "corr-1");

        assertEquals(200, r.getStatusCode().value());
        assertEquals(Boolean.TRUE, ckp.lastObservations.get("shock"));
        assertEquals(Boolean.FALSE, ckp.lastObservations.get("stroke"));
        assertEquals(340, ((Number) ckp.lastObservations.get("creatinineUmolL")).intValue());
    }

    @Test
    @DisplayName("A BLANK ANSWER IS OMITTED, never sent as false")
    void blankIsOmittedNotDenied() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "PRESENT",
                "nearMiss.cardiacArrest", "",
                "nearMiss.stroke", null,
                "nearMiss.creatinineUmolL", "")), "req-2", "corr-2");

        assertEquals(200, r.getStatusCode().value());
        // Sending false for these would let the engine walk to NEAR_MISS_NOT_MET on criteria nobody
        // assessed. Omitted, they stay unresolved and the outcome is honestly INDETERMINATE.
        assertFalse(ckp.lastObservations.containsKey("cardiacArrest"),
                "a blank must not be sent at all: " + ckp.lastObservations);
        assertFalse(ckp.lastObservations.containsKey("stroke"));
        assertFalse(ckp.lastObservations.containsKey("creatinineUmolL"));
        assertEquals(Boolean.TRUE, ckp.lastObservations.get("shock"));
    }

    @Test
    @DisplayName("the criteria left blank are named, because that is the clinician's next action")
    void blanksAreNamedNotCounted() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "ABSENT",
                "nearMiss.plateletsPerUl", "",
                "nearMiss.lactateMmolL", null)), "req-3", "corr-3");

        List<?> blank = (List<?>) meta(r).get("criteria_left_blank");
        assertTrue(blank.contains("plateletsPerUl"), "blanks must be named: " + blank);
        assertTrue(blank.contains("lactateMmolL"));
        assertEquals(1, meta(r).get("criteria_recorded"));
    }

    @Test
    @DisplayName("an explicit unknown behaves exactly like a blank, and is not an error")
    void explicitUnknownIsNotAnError() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "UNKNOWN",
                "nearMiss.stroke", "NOT_ASSESSED")), "req-4", "corr-4");

        assertEquals(200, r.getStatusCode().value());
        assertTrue(ckp.lastObservations.isEmpty(), "unknown must not become a fact: " + ckp.lastObservations);
        assertTrue(((List<?>) meta(r).get("criteria_left_blank")).contains("shock"));
    }

    @Test
    @DisplayName("AN UNREADABLE ANSWER IS REFUSED, not silently treated as absent")
    void unreadableAnswerIsRefused() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "PRESNT")), "req-5", "corr-5");

        // A typo becoming false is the whole failure mode: the clinician sees a classification that
        // silently ignored what she recorded, and NEAR_MISS_NOT_MET looks like a real assessment.
        assertEquals(422, r.getStatusCode().value());
        assertEquals("unrecognised_form_answer", r.getBody().get("error"));
        assertTrue(String.valueOf(r.getBody().get("unrecognised_answers")).contains("PRESNT"));
        // Nothing was classified on a body we could not read.
        assertNull(ckp.lastObservations);
    }

    @Test
    @DisplayName("a non-numeric measurement is refused rather than dropped")
    void nonNumericMeasurementIsRefused() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.plateletsPerUl", "thirty thousand")), "req-6", "corr-6");

        // Dropping it would be safe-but-silent: INDETERMINATE with no hint that the number she typed
        // never arrived.
        assertEquals(422, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody().get("unrecognised_answers")).contains("plateletsPerUl"));
    }

    @Test
    @DisplayName("a PRESENT code in a numeric field is refused, not coerced into a boolean")
    void codedValueInNumericFieldIsRefused() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.creatinineUmolL", "PRESENT")), "req-7", "corr-7");

        // The field's type is declared, not inferred from the value. Inferring would let a mis-keyed
        // answer assert renal failure on a woman whose creatinine was never measured.
        assertEquals(422, r.getStatusCode().value());
    }

    @Test
    @DisplayName("an unmapped linkId is ignored rather than failing the whole submission")
    void unknownLinkIdIsIgnored() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(answers(
                "nearMiss.shock", "PRESENT",
                "nearMiss.someFutureCriterion", "PRESENT",
                "encounterNote", "she arrived at 03:00")), "req-8", "corr-8");

        // Form 21 may gain a field before this map does. Refusing everything would block a
        // classification the known criteria already support, and an unmapped answer cannot cause a
        // false negative — it never becomes a fact, so its criterion stays unresolved.
        assertEquals(200, r.getStatusCode().value());
        assertEquals(1, ckp.lastObservations.size());
        assertEquals(Boolean.TRUE, ckp.lastObservations.get("shock"));
    }

    @Test
    @DisplayName("a bare field name works as well as a prefixed linkId")
    void bareFieldNamesAreAccepted() {
        StubCkp ckp = new StubCkp();
        controller(ckp).classifyFromForm(form(answers("shock", "PRESENT")), "req-9", "corr-9");

        assertEquals(Boolean.TRUE, ckp.lastObservations.get("shock"));
    }

    @Test
    @DisplayName("an empty form classifies as unassessed, not as a well woman")
    void emptyFormIsUnassessed() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(form(Map.of()), "req-10", "corr-10");

        assertEquals(200, r.getStatusCode().value());
        assertTrue(ckp.lastObservations.isEmpty());
        // CKP answers INDETERMINATE for an empty set; the BFF must not substitute anything for it.
        assertEquals(0, meta(r).get("criteria_recorded"));
    }

    @Test
    @DisplayName("an absent body does not 500")
    void absentBodyIsHandled() {
        StubCkp ckp = new StubCkp();
        var r = controller(ckp).classifyFromForm(null, "req-11", "corr-11");

        assertEquals(200, r.getStatusCode().value());
        assertTrue(ckp.lastObservations.isEmpty());
        // The form key falls back to form 21's own key rather than being reported as null.
        assertEquals("impilo.maternal.nearmiss.assessment.v1", meta(r).get("form_key"));
    }

    // ── Failure paths ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unreachable CKP is 502 with advice, never an empty all-clear")
    void unreachableCkpIsHonest() {
        StubCkp ckp = new StubCkp();
        ckp.toThrow = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
        var r = controller(ckp).classifyFromForm(form(answers("nearMiss.shock", "PRESENT")),
                "req-12", "corr-12");

        // An empty 200 renders as NEAR_MISS_NOT_MET in any client that reads only the code, so an
        // outage would appear as a clinical all-clear on a woman in shock.
        assertEquals(503, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody().get("upstream_status")).contains("503"));
    }

    @Test
    @DisplayName("a transport failure is 502 and says it is not a finding of no near-miss")
    void transportFailureIsHonest() {
        StubCkp ckp = new StubCkp();
        ckp.toThrow = new IllegalStateException("connection reset");
        var r = controller(ckp).classifyFromForm(form(answers("nearMiss.shock", "PRESENT")),
                "req-13", "corr-13");

        assertEquals(502, r.getStatusCode().value());
        assertEquals("near_miss_unavailable", r.getBody().get("error"));
        assertTrue(String.valueOf(r.getBody().get("message")).contains("not a finding"));
    }

    // ── Indicators ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the indicator request is forwarded verbatim")
    void indicatorRequestForwarded() {
        StubCkp ckp = new StubCkp();
        Map<String, Object> body = answers("indicatorPeriod", "2026-Q1",
                "cases", List.of(Map.of("outcome", "NEAR_MISS")));
        var r = controller(ckp).indicators(body, "req-14", "corr-14");

        assertEquals(200, r.getStatusCode().value());
        assertEquals("2026-Q1", ckp.lastIndicatorRequest.get("indicatorPeriod"));
    }

    @Test
    @DisplayName("CKP's 422 for an uncountable case reaches the clinician with its reason")
    void upstream422Passthrough() {
        StubCkp ckp = new StubCkp();
        ckp.toThrow = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity", new HttpHeaders(),
                "{\"data\":{\"error\":\"unclassifiable_outcome\",\"rejected_cases\":[\"case[7]: MAYBE\"]}}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        var r = controller(ckp).indicators(Map.of("cases", List.of()), "req-15", "corr-15");

        // Flattened to 502 this reads as an outage; the real answer is "case 7 cannot be counted".
        assertEquals(422, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody().get("upstream_body")).contains("case[7]"));
        assertTrue(String.valueOf(r.getBody().get("upstream_body")).contains("unclassifiable_outcome"));
    }
}
