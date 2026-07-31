package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for mental-health-service (port 8397) — the accepting counterparty for a
 * psychiatric emergency handover (W13). Trust headers are forwarded automatically by
 * {@code serviceRestTemplate}'s header-forwarding interceptor, same as every other client in this
 * package.
 *
 * <p>W15 completes the surface. W13 shipped referral intake/accept/decline only; the clinical
 * record behind an accepted referral — assessment, risk formulation, safety plan, involuntary
 * episode, restraint events, admission requests, follow-up — was written, migrated and contracted
 * but had no caller. Twenty-four operations existed; five were reachable.</p>
 *
 * <p><b>Every method unwraps the upstream {@code ApiResponse} envelope</b> via {@link
 * #extractData}, the same way {@code PctServiceClient} does. Before W15 these five referral methods
 * returned the whole envelope and {@code MentalHealthController} wrapped it again, so the queue page
 * received {@code {data:{success:true,data:[…]}}} and read {@code data.data} as an object rather
 * than an array. It would have thrown on the first real referral. The page's own test mocked the
 * single-wrapped shape, so the disagreement was invisible until the two were run together —
 * which, mental-health-service having never been deployed, had not happened.</p>
 */
@Component
public class MentalHealthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MentalHealthServiceClient.class);

    private static final String BASE_PATH = "/internal/v1/mental-health";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MentalHealthServiceClient(
            RestTemplate serviceRestTemplate,
            @Value("${impilo.services.mental-health-base-url:http://localhost:8397}") String baseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = baseUrl;
    }

    // ── Referrals ────────────────────────────────────────────────────────────────────────────

    public JsonNode intakeReferral(JsonNode body) {
        log.debug("Mental health: intake referral");
        return post("/referrals", body);
    }

    public JsonNode getReferral(String referralId) {
        return get("/referrals/" + referralId);
    }

    public JsonNode listReferrals(String status) {
        return get("/referrals?status=" + (status != null && !status.isBlank() ? status : "PENDING"));
    }

    public JsonNode acceptReferral(String referralId, JsonNode body) {
        log.debug("Mental health: accept referral={}", referralId);
        return post("/referrals/" + referralId + "/accept", body);
    }

    public JsonNode declineReferral(String referralId, JsonNode body) {
        log.debug("Mental health: decline referral={}", referralId);
        return post("/referrals/" + referralId + "/decline", body);
    }

    // ── Assessment and risk formulation ──────────────────────────────────────────────────────

    public JsonNode recordAssessment(String referralId, JsonNode body) {
        return post("/referrals/" + referralId + "/assessments", body);
    }

    public JsonNode listAssessments(String referralId) {
        return get("/referrals/" + referralId + "/assessments");
    }

    /**
     * Risk formulation hangs off an assessment, not off the referral — the risk is the conclusion of
     * a particular assessment on a particular day, and a later assessment reaches its own.
     */
    public JsonNode recordRiskFormulation(String assessmentId, JsonNode body) {
        return post("/assessments/" + assessmentId + "/risk-formulation", body);
    }

    public JsonNode listRiskFormulations(String assessmentId) {
        return get("/assessments/" + assessmentId + "/risk-formulation");
    }

    // ── Safety plan ──────────────────────────────────────────────────────────────────────────

    public JsonNode recordSafetyPlan(String referralId, JsonNode body) {
        return post("/referrals/" + referralId + "/safety-plans", body);
    }

    public JsonNode listSafetyPlans(String referralId) {
        return get("/referrals/" + referralId + "/safety-plans");
    }

    // ── Follow-up ────────────────────────────────────────────────────────────────────────────

    public JsonNode scheduleFollowup(String referralId, JsonNode body) {
        return post("/referrals/" + referralId + "/followups", body);
    }

    public JsonNode listFollowups(String referralId) {
        return get("/referrals/" + referralId + "/followups");
    }

    public JsonNode completeFollowup(String followupId, JsonNode body) {
        return post("/followups/" + followupId + "/complete", body);
    }

    // ── Involuntary episode ──────────────────────────────────────────────────────────────────

    public JsonNode openInvoluntaryEpisode(String referralId, JsonNode body) {
        log.info("Mental health: opening involuntary episode for referral={}", referralId);
        return post("/referrals/" + referralId + "/involuntary-episodes", body);
    }

    public JsonNode involuntaryEpisodeHistory(String referralId) {
        return get("/referrals/" + referralId + "/involuntary-episodes");
    }

    public JsonNode closeInvoluntaryEpisode(String episodeId, JsonNode body) {
        log.info("Mental health: closing involuntary episode={}", episodeId);
        return post("/involuntary-episodes/" + episodeId + "/close", body);
    }

    // ── Restraint events ─────────────────────────────────────────────────────────────────────

    public JsonNode recordRestraintEvent(String referralId, JsonNode body) {
        log.info("Mental health: recording restraint event for referral={}", referralId);
        return post("/referrals/" + referralId + "/restraint-events", body);
    }

    public JsonNode restraintEventHistory(String referralId) {
        return get("/referrals/" + referralId + "/restraint-events");
    }

    public JsonNode endRestraintEvent(String eventId) {
        return post("/restraint-events/" + eventId + "/end", null);
    }

    public JsonNode reviewRestraintEvent(String eventId, JsonNode body) {
        return post("/restraint-events/" + eventId + "/review", body);
    }

    /** The facility-wide review backlog: restraint that happened and has not yet been reviewed. */
    public JsonNode unreviewedRestraintEvents() {
        return get("/restraint-events/unreviewed");
    }

    // ── Admission requests ───────────────────────────────────────────────────────────────────

    public JsonNode raiseAdmissionRequest(String referralId, JsonNode body) {
        return post("/referrals/" + referralId + "/admission-requests", body);
    }

    public JsonNode admissionRequestHistory(String referralId) {
        return get("/referrals/" + referralId + "/admission-requests");
    }

    public JsonNode acknowledgeAdmissionRequest(String requestId, JsonNode body) {
        return post("/admission-requests/" + requestId + "/acknowledge", body);
    }

    // ── Transport ────────────────────────────────────────────────────────────────────────────

    private JsonNode get(String path) {
        return extractData(restTemplate.getForEntity(baseUrl + BASE_PATH + path, JsonNode.class));
    }

    private JsonNode post(String path, JsonNode body) {
        return extractData(restTemplate.postForEntity(baseUrl + BASE_PATH + path, body, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
