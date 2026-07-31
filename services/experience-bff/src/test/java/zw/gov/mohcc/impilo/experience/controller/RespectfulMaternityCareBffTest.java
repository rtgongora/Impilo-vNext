package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Respectful maternity care submission across Rito's two existing lanes.
 *
 * <p>What these tests protect: the woman's written account is filed before anything that can fail, the
 * anonymous report is never joined to the attributable rating, anonymity is the default rather than an
 * option she has to find, a report of being struck or detained does not get routed to the facility that
 * did it, and no score is ever stored with a polarity this service guessed at.
 */
class RespectfulMaternityCareBffTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private class StubCkp extends ClinicalKnowledgePlatformClient {
        Map<String, Object> lastNormaliseBody;
        boolean fail;

        StubCkp() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test"));
        }

        /** Mirrors CKP: already-normalised scores, so low means worse care for every measure. */
        @Override
        @SuppressWarnings("unchecked")
        public JsonNode respectfulCareNormalise(Map<String, Object> body) {
            if (fail) {
                throw new IllegalStateException("ckp down");
            }
            lastNormaliseBody = (Map<String, Object>) (Map<?, ?>) body;
            Map<String, BigDecimal> answers =
                    (Map<String, BigDecimal>) lastNormaliseBody.get("answers");

            ObjectNode data = mapper.createObjectNode();
            ArrayNode scores = data.putArray("scores");
            answers.forEach((measure, raw) -> {
                ObjectNode s = scores.addObject();
                s.put("domain", "CLIENT_EXPERIENCE");
                s.put("measure", measure);
                // never_left_alone is the reverse-scored item; CKP returns it already flipped.
                boolean reverse = measure.equals("never_left_alone");
                s.put("score", reverse ? BigDecimal.valueOf(6).subtract(raw) : raw);
                s.put("raw_score", raw);
                s.put("reverse_scored", reverse);
                s.put("scale", "1-5");
                s.put("mistreatment_category", switch (measure) {
                    case "physical_abuse_free" -> "PHYSICAL_ABUSE";
                    case "free_to_leave" -> "DETENTION_IN_FACILITY";
                    case "never_left_alone" -> "ABANDONMENT";
                    default -> "NON_DIGNIFIED_CARE";
                });
            });
            data.putArray("not_asked").add("free_to_leave");
            data.putArray("unknown_codes");
            return data;
        }

        @Override
        public JsonNode respectfulCareInstrument() {
            if (fail) {
                throw new IllegalStateException("ckp down");
            }
            ObjectNode data = mapper.createObjectNode();
            data.put("instrument_id", "RESPECTFUL_MATERNITY_CARE");
            return data;
        }
    }

    private class StubRito extends RitoServiceClient {
        Map<String, Object> lastCase;
        Map<String, Object> lastRating;
        boolean failCase;
        boolean failRating;

        StubRito() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode createPublicCase(Map<String, Object> body, String bearer) {
            if (failCase) {
                throw new IllegalStateException("rito down");
            }
            lastCase = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode n = mapper.createObjectNode();
            n.put("caseReference", "RITO-2026-000123");
            n.put("claimCode", "CLAIM-ABC-123");
            n.put("status", "RECEIVED");
            // Note what is NOT here: no internal case id. The lane withholds it deliberately.
            return n;
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonNode submitRating(Map<String, Object> body) {
            if (failRating) {
                throw new IllegalStateException("rito down");
            }
            lastRating = (Map<String, Object>) (Map<?, ?>) body;
            ObjectNode n = mapper.createObjectNode();
            n.put("id", "11111111-1111-1111-1111-111111111111");
            n.put("verifiedInteraction", body.get("encounterRef") != null);
            return n;
        }
    }

    private static RespectfulMaternityCareController controller(StubCkp ckp, StubRito rito) {
        return new RespectfulMaternityCareController(ckp, rito);
    }

    private static Map<String, BigDecimal> answers(Object... kv) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), BigDecimal.valueOf(((Number) kv[i + 1]).intValue()));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(ResponseEntity<Map<String, Object>> r, String key) {
        return (Map<String, Object>) data(r).get(key);
    }

    private static RespectfulMaternityCareController.FeedbackRequest feedback(
            Map<String, BigDecimal> answers, String narrative, Boolean identifyMe, String encounterRef) {
        return new RespectfulMaternityCareController.FeedbackRequest(
                "facility-1", encounterRef, encounterRef == null ? "PRV-1" : null, "2026-07",
                answers, narrative, identifyMe);
    }

    // ── The real measure codes reach Rito ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("scores are submitted under the real measure codes, never as a single 'overall'")
    void realMeasureCodesAreStored() {
        StubRito rito = new StubRito();
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 4, "consent_before_procedures", 5), null, null, "ENC-1"),
                "req-1", "corr-1", "token");

        assertEquals(200, r.getStatusCode().value());
        List<?> scores = (List<?>) rito.lastRating.get("domainScores");
        assertEquals(2, scores.size());
        assertTrue(scores.toString().contains("respect_dignity"));
        assertTrue(scores.toString().contains("CLIENT_EXPERIENCE"));
        // "overall" is what a rating carries when nobody built the instrument, and it cannot be acted on.
        assertFalse(scores.toString().contains("overall"));
        // No blended overall score either: a woman can be treated courteously and still be detained.
        assertNull(rito.lastRating.get("overallScore"));
    }

    @Test
    @DisplayName("the normalised score is stored, not the raw answer")
    void normalisedScoreIsStored() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("never_left_alone", 5), null, null, "ENC-1"),
                "req-2", "corr-2", "token");

        // She answered 5, meaning she was always left alone — the worst answer. Rito's aggregator takes
        // a plain mean with no polarity column, so storing 5 would record abandonment as excellent care.
        String stored = rito.lastRating.get("domainScores").toString();
        assertTrue(stored.contains("score=1"), "expected the flipped value, got: " + stored);
    }

    @Test
    @DisplayName("the polarity comes from CKP, so no local copy of the reverse list exists")
    void polarityIsDelegated() {
        StubCkp ckp = new StubCkp();
        controller(ckp, new StubRito()).submitFeedback(
                feedback(answers("never_left_alone", 2), null, null, "ENC-1"),
                "req-3", "corr-3", "token");

        // The BFF forwards raw answers and stores what comes back. If it decided polarity itself, a
        // content revision would need a BFF release and two callers could disagree about the same row.
        assertEquals(BigDecimal.valueOf(2),
                ((Map<?, ?>) ckp.lastNormaliseBody.get("answers")).get("never_left_alone"));
    }

    // ── Anonymity ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ANONYMOUS IS THE DEFAULT — identity protection is on unless she opts out")
    void anonymousByDefault() {
        StubRito rito = new StubRito();
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 2), "I was shouted at", null, "ENC-1"),
                "req-4", "corr-4", "token");

        // An instrument that works only when signed measures the women least at risk.
        assertEquals(Boolean.TRUE, rito.lastRating.get("respondentIdentityProtected"));
        assertEquals(true, data(r).get("anonymous"));
    }

    @Test
    @DisplayName("an explicit opt-in is honoured, and only an explicit one")
    void explicitIdentificationIsHonoured() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 5), null, Boolean.TRUE, "ENC-1"),
                "req-5", "corr-5", "token");

        assertEquals(Boolean.FALSE, rito.lastRating.get("respondentIdentityProtected"));
    }

    @Test
    @DisplayName("THE ANONYMOUS REPORT IS NEVER LINKED TO THE RATING")
    void narrativeIsNotLinkedToRating() {
        StubRito rito = new StubRito();
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 1), "The midwife slapped me", null, "ENC-1"),
                "req-6", "corr-6", "token");

        // The rating carries the provider and the encounter. A link would hand the identity behind the
        // anonymous report to anyone who can read the rating.
        assertNull(rito.lastRating.get("narrativeCaseId"));
        assertEquals(false, data(r).get("narrative_linked_to_rating"));
        assertTrue(String.valueOf(data(r).get("linkage_note")).contains("would identify"));
    }

    @Test
    @DisplayName("the anonymous case carries no provider, encounter or contact")
    void anonymousCaseCarriesNoIdentifiers() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 1), "I was left alone all night", null, "ENC-1"),
                "req-7", "corr-7", "token");

        // The encounter reference alone identifies her, so the anonymous lane must not carry it even
        // though the caller supplied it for the rating.
        assertNull(rito.lastCase.get("encounterRef"));
        assertNull(rito.lastCase.get("providerPublicId"));
        assertNull(rito.lastCase.get("contact"));
        // The facility is kept: without it the report cannot be acted on at all.
        assertEquals("facility-1", rito.lastCase.get("facilityId"));
    }

    @Test
    @DisplayName("the claim code is returned, since it is her only way back to the case")
    void claimCodeIsReturned() {
        var r = controller(new StubCkp(), new StubRito()).submitFeedback(
                feedback(null, "I was detained over the bill", null, null),
                "req-8", "corr-8", "token");

        assertEquals(true, section(r, "narrative").get("filed"));
        assertEquals("CLAIM-ABC-123", section(r, "narrative").get("claim_code"));
        assertEquals("RITO-2026-000123", section(r, "narrative").get("case_reference"));
    }

    // ── Routing: her report must not go to the people she is reporting ─────────────────────────────

    @Test
    @DisplayName("a report of physical abuse is escalated away from the facility's own queue")
    void physicalAbuseEscalates() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("physical_abuse_free", 1), "She hit me during the birth", null, "ENC-1"),
                "req-9", "corr-9", "token");

        // COMPLAINT_ABOUT_CARE routes ROUTINE to facility and district complaints — the people being
        // reported. PATIENT_SAFETY routes CRITICAL to patient-safety QA instead.
        assertEquals("PATIENT_SAFETY", rito.lastCase.get("feedbackCategory"));
    }

    @Test
    @DisplayName("detention for an unpaid bill escalates the same way")
    void detentionEscalates() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("free_to_leave", 1), "They would not let me go", null, "ENC-1"),
                "req-10", "corr-10", "token");

        assertEquals("PATIENT_SAFETY", rito.lastCase.get("feedbackCategory"));
    }

    @Test
    @DisplayName("an ordinary poor-experience report routes as a care complaint")
    void poorExperienceRoutesAsComplaint() {
        StubRito rito = new StubRito();
        controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("communication_understood", 2), "Nobody explained anything", null, "ENC-1"),
                "req-11", "corr-11", "token");

        assertEquals("COMPLAINT_ABOUT_CARE", rito.lastCase.get("feedbackCategory"));
    }

    @Test
    @DisplayName("uniformly good scores route as a compliment, but silence never does")
    void complimentRequiresAnswers() {
        StubRito good = new StubRito();
        controller(new StubCkp(), good).submitFeedback(
                feedback(answers("respect_dignity", 5, "pain_addressed", 5), "They were kind", null, "ENC-1"),
                "req-12", "corr-12", "token");
        assertEquals("COMPLIMENT", good.lastCase.get("feedbackCategory"));

        StubRito silent = new StubRito();
        controller(new StubCkp(), silent).submitFeedback(
                feedback(null, "Something happened I want to report", null, null),
                "req-13", "corr-13", "token");
        // Filing a woman's silence as praise is the specific error here.
        assertEquals("COMPLAINT_ABOUT_CARE", silent.lastCase.get("feedbackCategory"));
    }

    // ── Failure paths: her account must survive them ───────────────────────────────────────────────

    @Test
    @DisplayName("HER ACCOUNT IS FILED EVEN WHEN THE RATING CANNOT BE STORED")
    void narrativeSurvivesRatingFailure() {
        StubRito rito = new StubRito();
        rito.failRating = true;
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 1), "This is what happened to me", null, "ENC-1"),
                "req-14", "corr-14", "token");

        // The account is the part she may not be able to give again. It is filed first for exactly this.
        assertEquals(true, section(r, "narrative").get("filed"));
        assertEquals("CLAIM-ABC-123", section(r, "narrative").get("claim_code"));
        assertEquals(false, section(r, "scores").get("stored"));
        assertEquals("rating_unavailable", section(r, "scores").get("reason"));
    }

    @Test
    @DisplayName("a failed narrative filing says so plainly, so she knows to keep what she wrote")
    void failedNarrativeIsHonest() {
        StubRito rito = new StubRito();
        rito.failCase = true;
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(answers("respect_dignity", 3), "I want to report something", null, "ENC-1"),
                "req-15", "corr-15", "token");

        assertEquals(false, section(r, "narrative").get("filed"));
        assertTrue(String.valueOf(section(r, "narrative").get("message")).contains("has not been recorded"));
    }

    @Test
    @DisplayName("scores are refused rather than stored with a guessed polarity")
    void scoresRefusedWhenPolarityUnknown() {
        StubCkp ckp = new StubCkp();
        ckp.fail = true;
        StubRito rito = new StubRito();
        var r = controller(ckp, rito).submitFeedback(
                feedback(answers("never_left_alone", 5), "I was alone", null, "ENC-1"),
                "req-16", "corr-16", "token");

        // Storing raw would put the reverse-scored item in backwards, pulling the facility's mean the
        // wrong way, invisibly to every later reader. Nothing was sent to the rating lane.
        assertEquals(false, section(r, "scores").get("stored"));
        assertEquals("instrument_unavailable", section(r, "scores").get("reason"));
        assertNull(rito.lastRating);
        // And her account still went in.
        assertEquals(true, section(r, "narrative").get("filed"));
    }

    @Test
    @DisplayName("scores with no attribution are refused honestly, and the narrative still stands")
    void unattributableScoresAreRefused() {
        StubRito rito = new StubRito();
        var request = new RespectfulMaternityCareController.FeedbackRequest(
                "facility-1", null, null, "2026-07",
                answers("respect_dignity", 2), "It was not good", null);
        var r = controller(new StubCkp(), rito).submitFeedback(request, "req-17", "corr-17", "token");

        // Rito's rating hangs off a provider. Inventing an attribution would be worse than saying so.
        assertEquals(false, section(r, "scores").get("stored"));
        assertEquals("no_attribution", section(r, "scores").get("reason"));
        assertNull(rito.lastRating);
        assertEquals(true, section(r, "narrative").get("filed"));
    }

    @Test
    @DisplayName("an empty submission is rejected rather than filed as an empty report")
    void emptySubmissionRejected() {
        StubRito rito = new StubRito();
        var r = controller(new StubCkp(), rito).submitFeedback(
                feedback(null, null, null, "ENC-1"), "req-18", "corr-18", "token");

        assertEquals(400, r.getStatusCode().value());
        assertEquals("empty_submission", r.getBody().get("error"));
        assertNull(rito.lastCase);
        assertNull(rito.lastRating);
    }

    @Test
    @DisplayName("unasked measures are named on the result, so coverage is visible")
    void notAskedIsReported() {
        var r = controller(new StubCkp(), new StubRito()).submitFeedback(
                feedback(answers("respect_dignity", 4), null, null, "ENC-1"),
                "req-19", "corr-19", "token");

        // A programme cannot learn that nobody asks about detention from a count.
        assertTrue(((List<?>) section(r, "scores").get("not_asked")).contains("free_to_leave"));
    }

    @Test
    @DisplayName("the firewall statement travels with a stored rating")
    void firewallIsCarried() {
        var r = controller(new StubCkp(), new StubRito()).submitFeedback(
                feedback(answers("respect_dignity", 1), null, null, "ENC-1"),
                "req-20", "corr-20", "token");

        assertTrue(String.valueOf(section(r, "scores").get("regulation_firewall"))
                .contains("does not affect any licence"));
    }

    @Test
    @DisplayName("an unreachable instrument does not invite the client to use its own copy")
    void instrumentFailureWarnsAgainstLocalCopy() {
        StubCkp ckp = new StubCkp();
        ckp.fail = true;
        var r = controller(ckp, new StubRito()).instrument("req-21", "corr-21");

        assertEquals(502, r.getStatusCode().value());
        assertTrue(String.valueOf(r.getBody().get("message")).contains("governed content"));
    }
}
