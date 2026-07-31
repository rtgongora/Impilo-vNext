package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.RitoServiceClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respectful maternity care: the instrument a woman is shown, and the submission of her answers through
 * the two lanes Rito already has.
 *
 * <h2>Two lanes, deliberately not joined</h2>
 * <p>The narrative goes to the anonymous public case lane and the scores go to the rating lane, and this
 * controller does <b>not</b> link them. It could not even if it wanted to — the public lane returns a
 * case reference and a claim code but never the internal case id, so there is no handle to put in the
 * rating's {@code narrativeCaseId}. That withholding is a privacy property rather than a gap: a rating
 * carries the provider and the encounter, so joining an anonymous narrative to one would hand anybody
 * reading the rating the identity behind the anonymous report. The absence of a link is the feature.
 *
 * <h2>Anonymous is the default, not an option she has to find</h2>
 * <p>{@code respondentIdentityProtected} defaults to true and only an explicit {@code identifyMe}
 * turns it off. A woman still in the ward, or who will come back for her next birth, cannot safely
 * attribute a complaint about the staff caring for her; an instrument that works only when signed
 * measures the women least at risk and reports their answers as the facility's experience.
 *
 * <h2>Her report is filed before anything that can fail</h2>
 * <p>The narrative is submitted first. Rating attribution can fail for reasons that have nothing to do
 * with what she said — no provider named, no matching encounter — and if it were attempted first a
 * failure there would throw away the account she may not be able to give again.
 *
 * <h2>The firewall</h2>
 * <p>Rito's rule is inherited unchanged: nothing here mutates a licence, scope, employment or access.
 * Scores land as feedback; patterns become governed referrals through Rito's own moderation and
 * signal machinery, never through this route.
 */
@RestController
@RequestMapping("/internal/v1/maternity/respectful-care")
public class RespectfulMaternityCareController {

    private static final Logger log = LoggerFactory.getLogger(RespectfulMaternityCareController.class);

    /** Rito's existing citizen-facing category for a care-experience complaint. */
    private static final String CATEGORY_CARE_COMPLAINT = "COMPLAINT_ABOUT_CARE";
    /** Rito's existing safety category: CRITICAL, and owned by patient-safety QA rather than the facility. */
    private static final String CATEGORY_PATIENT_SAFETY = "PATIENT_SAFETY";
    private static final String CATEGORY_COMPLIMENT = "COMPLIMENT";

    /**
     * The mistreatment categories that must never be routed to the facility that caused them.
     * COMPLAINT_ABOUT_CARE routes ROUTINE to facility and district complaints, which for a woman
     * reporting that she was struck or held for an unpaid bill means her report arrives at the people
     * she is reporting. PATIENT_SAFETY routes CRITICAL to patient-safety QA instead.
     */
    private static final List<String> ESCALATING_CATEGORIES = List.of(
            "PHYSICAL_ABUSE", "DETENTION_IN_FACILITY", "NON_CONSENTED_CARE");

    private final ClinicalKnowledgePlatformClient ckp;
    private final RitoServiceClient rito;

    public RespectfulMaternityCareController(ClinicalKnowledgePlatformClient ckp,
                                            RitoServiceClient rito) {
        this.ckp = ckp;
        this.rito = rito;
    }

    /**
     * @param answers   raw answers keyed by measure code, exactly as the woman gave them; the polarity
     *                  flip is CKP's, not the client's
     * @param narrative her account in her own words, filed anonymously
     * @param identifyMe explicit opt-in to being identifiable; absent means anonymous
     */
    public record FeedbackRequest(
            String facilityId,
            String encounterRef,
            String providerPublicId,
            String reportingPeriod,
            Map<String, BigDecimal> answers,
            String narrative,
            Boolean identifyMe) {
    }

    /** The measure set to render. Proxied so no client keeps its own copy of the questions. */
    @GetMapping("/instrument")
    public ResponseEntity<Map<String, Object>> instrument(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = ckp.respectfulCareInstrument();
            return ResponseEntity.ok(Map.of(
                    "data", data == null ? Map.of() : data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("CKP respectful-care instrument failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "instrument_unavailable",
                    // A client falling back to a hardcoded copy is the specific harm here: the polarity
                    // of a reverse-scored item is content, and a stale copy stores it backwards.
                    "message", "The measure set could not be retrieved. Do not fall back to a local "
                            + "copy of the questions; the scoring polarity is governed content.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @RequestBody(required = false) FeedbackRequest request,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = "X-Service-Token", required = false) String serviceToken) {

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "empty_submission",
                    "message", "No answers and no narrative were submitted.",
                    "meta", meta));
        }

        boolean hasNarrative = request.narrative() != null && !request.narrative().isBlank();
        boolean hasAnswers = request.answers() != null && !request.answers().isEmpty();
        if (!hasNarrative && !hasAnswers) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "empty_submission",
                    "message", "No answers and no narrative were submitted.",
                    "meta", meta));
        }

        // ── 1. Normalise, so polarity is decided by the content and not by this controller ──────────
        JsonNode normalised = null;
        if (hasAnswers) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("answers", request.answers());
                normalised = ckp.respectfulCareNormalise(body);
            } catch (Exception e) {
                log.error("CKP respectful-care normalise failed: {}", e.getMessage());
                // Storing raw would put a negatively-worded item in backwards and pull the facility's
                // mean the wrong way, invisibly to every later reader. Refuse the scores; her narrative
                // is still filed below.
                normalised = null;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();

        // ── 2. Her account first, anonymously, before anything that can fail on attribution ─────────
        if (hasNarrative) {
            Map<String, Object> caseBody = new LinkedHashMap<>();
            caseBody.put("caseType", "COMPLAINT");
            caseBody.put("feedbackCategory", categoryFor(normalised, request.answers()));
            caseBody.put("title", "Respectful maternity care report");
            caseBody.put("description", request.narrative().trim());
            caseBody.put("facilityId", request.facilityId());
            // No provider, no encounter reference and no contact. Each would turn the anonymous lane
            // into an attributable one, and the encounter reference alone identifies her.
            try {
                JsonNode result = rito.createPublicCase(caseBody, serviceToken);
                Map<String, Object> narrativeResult = new LinkedHashMap<>();
                narrativeResult.put("filed", true);
                narrativeResult.put("case_reference", text(result, "caseReference"));
                // The claim code is how she checks on it later without ever identifying herself. It is
                // returned once and not recoverable, so a client that drops it has lost her access.
                narrativeResult.put("claim_code", text(result, "claimCode"));
                narrativeResult.put("routed_as", caseBody.get("feedbackCategory"));
                data.put("narrative", narrativeResult);
            } catch (Exception e) {
                log.error("Rito anonymous case intake failed: {}", e.getMessage());
                Map<String, Object> narrativeResult = new LinkedHashMap<>();
                narrativeResult.put("filed", false);
                narrativeResult.put("message", "Your report was not filed. Keep what you wrote and try "
                        + "again — it has not been recorded.");
                data.put("narrative", narrativeResult);
            }
        }

        // ── 3. The scores, as an ordinary rating carrying the real measure codes ────────────────────
        if (hasAnswers) {
            data.put("scores", submitScores(request, normalised));
        }

        data.put("anonymous", !Boolean.TRUE.equals(request.identifyMe()));
        data.put("narrative_linked_to_rating", false);
        data.put("linkage_note", "The anonymous report and the scored rating are deliberately not "
                + "linked. A rating carries the provider and the encounter, so a link would identify "
                + "the author of the anonymous report to anyone who can read the rating.");
        return ResponseEntity.ok(Map.of("data", data, "meta", meta));
    }

    private Map<String, Object> submitScores(FeedbackRequest request, JsonNode normalised) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (normalised == null) {
            result.put("stored", false);
            result.put("reason", "instrument_unavailable");
            result.put("message", "The scoring polarity could not be resolved, so the answers were not "
                    + "stored rather than being stored backwards.");
            return result;
        }

        List<Map<String, Object>> domainScores = new ArrayList<>();
        for (JsonNode s : normalised.path("scores")) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("domain", s.path("domain").asText());
            row.put("measure", s.path("measure").asText());
            // The normalised value, never the raw one: Rito's aggregator takes a plain mean and has no
            // polarity column to consult.
            row.put("score", s.path("score").decimalValue());
            row.put("scale", s.path("scale").asText());
            domainScores.add(row);
        }

        if (domainScores.isEmpty()) {
            result.put("stored", false);
            result.put("reason", "no_scorable_answers");
            result.put("not_asked", texts(normalised.path("not_asked")));
            result.put("unknown_codes", texts(normalised.path("unknown_codes")));
            return result;
        }

        boolean attributable = notBlank(request.providerPublicId()) || notBlank(request.encounterRef());
        if (!attributable) {
            // Rito's rating hangs off a provider. Rather than inventing an attribution, say so — and
            // note her narrative is already filed, so nothing she wrote depends on this.
            result.put("stored", false);
            result.put("reason", "no_attribution");
            result.put("message", "Scores attach to a rating, which needs either the encounter "
                    + "reference from the feedback link or a named provider. The written report, if "
                    + "there was one, has already been filed and does not need either.");
            return result;
        }

        Map<String, Object> ratingBody = new LinkedHashMap<>();
        ratingBody.put("providerPublicId", request.providerPublicId());
        ratingBody.put("facilityId", request.facilityId());
        ratingBody.put("encounterRef", request.encounterRef());
        ratingBody.put("respondentClass", "CLIENT");
        // Anonymous unless she explicitly said otherwise. Rito drops the actor reference entirely when
        // this is true, so the protection is in the store and not just in this request.
        ratingBody.put("respondentIdentityProtected", !Boolean.TRUE.equals(request.identifyMe()));
        ratingBody.put("reportingPeriod", request.reportingPeriod());
        // No overallScore. Rito's four domains are non-blendable and an average across ten measures
        // hides the one that mattered — a woman can be treated courteously and still be detained.
        ratingBody.put("domainScores", domainScores);

        try {
            JsonNode rating = rito.submitRating(ratingBody);
            result.put("stored", true);
            result.put("rating_id", text(rating, "id"));
            result.put("verified_interaction", rating != null && rating.path("verifiedInteraction").asBoolean(false));
            result.put("measures_stored", domainScores.size());
            // Named, so a programme can see that nobody is being asked about detention.
            result.put("not_asked", texts(normalised.path("not_asked")));
            result.put("regulation_firewall", "This rating does not affect any licence, scope, "
                    + "employment or access. Patterns become governed referrals; a single report never "
                    + "becomes a disciplinary act.");
        } catch (Exception e) {
            log.error("Rito rating submission failed: {}", e.getMessage());
            result.put("stored", false);
            result.put("reason", "rating_unavailable");
            result.put("message", "The scores were not stored. Any written report was filed separately "
                    + "and is unaffected.");
        }
        return result;
    }

    /**
     * Chooses the routing category for the anonymous case.
     *
     * <p>A report of physical abuse, detention or a procedure done without consent is escalated away
     * from the facility's own complaints queue. Routed as an ordinary care complaint it would arrive,
     * ROUTINE, at the facility and district being complained about.
     */
    private static String categoryFor(JsonNode normalised, Map<String, BigDecimal> answers) {
        if (normalised == null || answers == null) {
            return CATEGORY_CARE_COMPLAINT;
        }

        boolean anyBad = false;
        boolean allGood = true;
        for (JsonNode s : normalised.path("scores")) {
            // The stored value is already polarity-normalised, so "low is bad" holds for every measure
            // and this comparison needs no per-measure knowledge.
            BigDecimal score = s.path("score").decimalValue();
            boolean bad = score.compareTo(BigDecimal.valueOf(3)) < 0;
            if (bad) {
                anyBad = true;
                allGood = false;
                if (ESCALATING_CATEGORIES.contains(s.path("mistreatment_category").asText(""))) {
                    return CATEGORY_PATIENT_SAFETY;
                }
            } else if (score.compareTo(BigDecimal.valueOf(4)) < 0) {
                allGood = false;
            }
        }

        // A compliment only when nothing was poor and something was actually answered. Defaulting an
        // unanswered submission to COMPLIMENT would file a woman's silence as praise.
        if (allGood && !anyBad && normalised.path("scores").size() > 0) {
            return CATEGORY_COMPLIMENT;
        }
        return CATEGORY_CARE_COMPLAINT;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull()
                ? null : node.path(field).asText();
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            for (JsonNode n : array) {
                out.add(n.asText());
            }
        }
        return out;
    }
}
