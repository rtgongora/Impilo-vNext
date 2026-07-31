package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.MentalHealthServiceClient;

import java.util.Map;
import java.util.function.Supplier;

/**
 * BFF proxy for mental-health-service — the accepting side of a psychiatric emergency handover and
 * the clinical record that follows acceptance.
 *
 * <p>W13 shipped the referral acceptance surface; W15 adds the twenty-four-operation clinical
 * record behind it. Assessment, risk formulation, safety plan, involuntary episode, restraint
 * events, admission requests and follow-up were all built, migrated, contracted and tested in
 * mental-health-service, and none of them had a caller — the service could be exercised only from
 * inside the cluster.</p>
 *
 * <p>Two behaviours matter more here than on most proxies:</p>
 *
 * <ul>
 *   <li><b>4xx passes through unchanged.</b> mental-health-service refuses for reasons a clinician
 *       needs to read: a second open involuntary episode (409), a restraint review with no outcome,
 *       a capacity outcome without a capacity assessment. Collapsing those to 502 would report a
 *       lawful refusal as an outage.</li>
 *   <li><b>Only a real outage becomes 502.</b> mental-health-service has no built image and no
 *       deployment, so in the preview estate every one of these routes will return 502 today. That
 *       is the honest answer and the UI is built to say so — it is not the same as "this patient
 *       has no restraint events".</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/mental-health")
public class MentalHealthController {

    private static final Logger log = LoggerFactory.getLogger(MentalHealthController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MentalHealthServiceClient client;

    public MentalHealthController(MentalHealthServiceClient client) {
        this.client = client;
    }

    // ── Referrals (W13) ──────────────────────────────────────────────────────────────────────

    @PostMapping("/referrals")
    public ResponseEntity<Map<String, Object>> intake(@RequestBody Map<String, Object> body) {
        return proxy(() -> client.intakeReferral(toJson(body)), "mental-health intake", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return proxy(() -> client.getReferral(id), "mental-health getReferral", HttpStatus.OK);
    }

    @GetMapping("/referrals")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String status) {
        return proxy(() -> client.listReferrals(status), "mental-health listReferrals", HttpStatus.OK);
    }

    @PostMapping("/referrals/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.acceptReferral(id, toJson(body)), "mental-health acceptReferral", HttpStatus.OK);
    }

    @PostMapping("/referrals/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.declineReferral(id, toJson(body)), "mental-health declineReferral", HttpStatus.OK);
    }

    // ── Assessment and risk formulation (W15) ────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/assessments")
    public ResponseEntity<Map<String, Object>> recordAssessment(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.recordAssessment(referralId, toJson(body)),
                "mental-health recordAssessment", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/assessments")
    public ResponseEntity<Map<String, Object>> listAssessments(@PathVariable String referralId) {
        return proxy(() -> client.listAssessments(referralId), "mental-health listAssessments", HttpStatus.OK);
    }

    @PostMapping("/assessments/{assessmentId}/risk-formulation")
    public ResponseEntity<Map<String, Object>> recordRiskFormulation(
            @PathVariable String assessmentId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.recordRiskFormulation(assessmentId, toJson(body)),
                "mental-health recordRiskFormulation", HttpStatus.CREATED);
    }

    @GetMapping("/assessments/{assessmentId}/risk-formulation")
    public ResponseEntity<Map<String, Object>> listRiskFormulations(@PathVariable String assessmentId) {
        return proxy(() -> client.listRiskFormulations(assessmentId),
                "mental-health listRiskFormulations", HttpStatus.OK);
    }

    // ── Safety plan (W15) ────────────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/safety-plans")
    public ResponseEntity<Map<String, Object>> recordSafetyPlan(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.recordSafetyPlan(referralId, toJson(body)),
                "mental-health recordSafetyPlan", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/safety-plans")
    public ResponseEntity<Map<String, Object>> listSafetyPlans(@PathVariable String referralId) {
        return proxy(() -> client.listSafetyPlans(referralId), "mental-health listSafetyPlans", HttpStatus.OK);
    }

    // ── Follow-up (W15) ──────────────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/followups")
    public ResponseEntity<Map<String, Object>> scheduleFollowup(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.scheduleFollowup(referralId, toJson(body)),
                "mental-health scheduleFollowup", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/followups")
    public ResponseEntity<Map<String, Object>> listFollowups(@PathVariable String referralId) {
        return proxy(() -> client.listFollowups(referralId), "mental-health listFollowups", HttpStatus.OK);
    }

    @PostMapping("/followups/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeFollowup(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return proxy(() -> client.completeFollowup(id, toJson(body)),
                "mental-health completeFollowup", HttpStatus.OK);
    }

    // ── Involuntary episode (W15) ────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/involuntary-episodes")
    public ResponseEntity<Map<String, Object>> openInvoluntaryEpisode(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.openInvoluntaryEpisode(referralId, toJson(body)),
                "mental-health openInvoluntaryEpisode", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/involuntary-episodes")
    public ResponseEntity<Map<String, Object>> involuntaryEpisodeHistory(@PathVariable String referralId) {
        return proxy(() -> client.involuntaryEpisodeHistory(referralId),
                "mental-health involuntaryEpisodeHistory", HttpStatus.OK);
    }

    @PostMapping("/involuntary-episodes/{id}/close")
    public ResponseEntity<Map<String, Object>> closeInvoluntaryEpisode(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.closeInvoluntaryEpisode(id, toJson(body)),
                "mental-health closeInvoluntaryEpisode", HttpStatus.OK);
    }

    // ── Restraint events (W15) ───────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/restraint-events")
    public ResponseEntity<Map<String, Object>> recordRestraintEvent(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.recordRestraintEvent(referralId, toJson(body)),
                "mental-health recordRestraintEvent", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/restraint-events")
    public ResponseEntity<Map<String, Object>> restraintEventHistory(@PathVariable String referralId) {
        return proxy(() -> client.restraintEventHistory(referralId),
                "mental-health restraintEventHistory", HttpStatus.OK);
    }

    @PostMapping("/restraint-events/{id}/end")
    public ResponseEntity<Map<String, Object>> endRestraintEvent(@PathVariable String id) {
        return proxy(() -> client.endRestraintEvent(id), "mental-health endRestraintEvent", HttpStatus.OK);
    }

    @PostMapping("/restraint-events/{id}/review")
    public ResponseEntity<Map<String, Object>> reviewRestraintEvent(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.reviewRestraintEvent(id, toJson(body)),
                "mental-health reviewRestraintEvent", HttpStatus.OK);
    }

    /**
     * The review backlog. Registered above {@code /restraint-events/{id}} would matter if such a
     * route existed; it does not, so "unreviewed" cannot be swallowed as an id.
     */
    @GetMapping("/restraint-events/unreviewed")
    public ResponseEntity<Map<String, Object>> unreviewedRestraintEvents() {
        return proxy(client::unreviewedRestraintEvents, "mental-health unreviewedRestraintEvents", HttpStatus.OK);
    }

    // ── Admission requests (W15) ─────────────────────────────────────────────────────────────

    @PostMapping("/referrals/{referralId}/admission-requests")
    public ResponseEntity<Map<String, Object>> raiseAdmissionRequest(
            @PathVariable String referralId, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.raiseAdmissionRequest(referralId, toJson(body)),
                "mental-health raiseAdmissionRequest", HttpStatus.CREATED);
    }

    @GetMapping("/referrals/{referralId}/admission-requests")
    public ResponseEntity<Map<String, Object>> admissionRequestHistory(@PathVariable String referralId) {
        return proxy(() -> client.admissionRequestHistory(referralId),
                "mental-health admissionRequestHistory", HttpStatus.OK);
    }

    @PostMapping("/admission-requests/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAdmissionRequest(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return proxy(() -> client.acknowledgeAdmissionRequest(id, toJson(body)),
                "mental-health acknowledgeAdmissionRequest", HttpStatus.OK);
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────────────────────

    private JsonNode toJson(Map<String, Object> body) {
        return MAPPER.valueToTree(body != null ? body : Map.of());
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private ResponseEntity<Map<String, Object>> proxy(
            Supplier<JsonNode> call, String op, HttpStatus successStatus) {
        try {
            JsonNode data = requirePayload(call.get(), op);
            return ResponseEntity.status(successStatus).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            throw upstreamFailure(op, e);
        } catch (Exception e) {
            throw upstreamFailure(op, e);
        }
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        log.warn("{} failed: {}", operation, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }
}
