package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TelemonitoringServiceClient;

/**
 * OF-B23/B27/B28 — Wave OF-C monitoring surfaces proxy over telemonitoring-service
 * (plans, programmes, device assignments, readings, alert episodes).
 *
 * <p>Pure stateless composition (experience-bff holds no truth): every upstream
 * payload is forwarded verbatim so telemonitoring's coded error envelope
 * ({@code {error:{code,message},timestamp}} — e.g. TM_ALERT_INVALID_TRANSITION,
 * TM_CLOSURE_INCOMPLETE, TM_INVALID_REQUEST) reaches the shell with its REAL
 * status and code. Masking a guarded state-machine rejection as a 502 would turn
 * honest §14.6 accountable-closure refusals into fake outages — the same
 * passthrough doctrine as {@code MarketplaceRequestBffController}. Only
 * transport-level failures (connection refused/timeout) become a coded 502
 * {@code TELEMONITORING_UNAVAILABLE} envelope.</p>
 *
 * <p>PII floor: telemonitoring is CPID-only by construction (no names, no
 * contact data) — this controller adds nothing and strips nothing. The citizen
 * self lanes ({@code /my/**}) resolve the patient from {@code X-Actor-ID}
 * (CPID == health id per the identity-plane collapse), the same pattern as
 * {@code CitizenTelehealthController} — a citizen never supplies a CPID query
 * parameter and therefore can never query another patient's plans through
 * this surface.</p>
 */
@RestController
@RequestMapping("/internal/v1/telemonitoring")
public class TelemonitoringBffController {

    private static final Logger log = LoggerFactory.getLogger(TelemonitoringBffController.class);

    private final TelemonitoringServiceClient telemonitoringClient;

    public TelemonitoringBffController(TelemonitoringServiceClient telemonitoringClient) {
        this.telemonitoringClient = telemonitoringClient;
    }

    // ── Plans (OF-B28 command workspace: lookup by patient; B27 self lane below) ──

    /** Clinician lane — plans for an explicit patient CPID (the upstream list is patient-scoped). */
    @GetMapping("/plans")
    public ResponseEntity<String> listPlans(
            @RequestParam String patientCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("plan listing", reqId, correlationId,
                () -> telemonitoringClient.listPlansByPatient(patientCpid));
    }

    /** B27 citizen self lane — my plans, patient resolved from the trust-plane actor. */
    @GetMapping("/my/plans")
    public ResponseEntity<String> listMyPlans(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("my-plan listing", reqId, correlationId,
                () -> telemonitoringClient.listPlansByPatient(actorId));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<String> getPlan(
            @PathVariable String planId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("plan fetch", reqId, correlationId,
                () -> telemonitoringClient.getPlan(planId));
    }

    /** Record a completed clinical review — re-arms the plan's review-cadence timer. */
    @PostMapping("/plans/{planId}/reviews")
    public ResponseEntity<String> recordReview(
            @PathVariable String planId,
            @RequestBody(required = false) String body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("review recording", reqId, correlationId,
                () -> telemonitoringClient.recordReview(planId, body, idempotencyKey));
    }

    /** Threshold-profile version history — the highest version is the current thresholds. */
    @GetMapping("/plans/{planId}/threshold-profiles")
    public ResponseEntity<String> listThresholdProfiles(
            @PathVariable String planId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("threshold-profile listing", reqId, correlationId,
                () -> telemonitoringClient.listThresholdProfiles(planId));
    }

    // ── Programme catalogue (§14.1 profile catalogue — reads for every consumer) ──

    @GetMapping("/programmes")
    public ResponseEntity<String> listProgrammes(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("programme listing", reqId, correlationId,
                telemonitoringClient::listProgrammes);
    }

    @GetMapping("/programmes/{code}")
    public ResponseEntity<String> getProgramme(
            @PathVariable String code,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("programme fetch", reqId, correlationId,
                () -> telemonitoringClient.getProgramme(code));
    }

    // ── Device assignments (OF-B24 — honest posture from the assignment gate) ──

    /** Clinician lane — assignments by plan or explicit patient CPID (upstream requires one). */
    @GetMapping("/device-assignments")
    public ResponseEntity<String> listDeviceAssignments(
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String patientCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("assignment listing", reqId, correlationId,
                () -> telemonitoringClient.listDeviceAssignments(planId, patientCpid));
    }

    /** B27 citizen self lane — my device assignments, patient resolved from the actor. */
    @GetMapping("/my/device-assignments")
    public ResponseEntity<String> listMyDeviceAssignments(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("my-assignment listing", reqId, correlationId,
                () -> telemonitoringClient.listDeviceAssignments(null, actorId));
    }

    @GetMapping("/device-assignments/{assignmentId}")
    public ResponseEntity<String> getDeviceAssignment(
            @PathVariable String assignmentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("assignment fetch", reqId, correlationId,
                () -> telemonitoringClient.getDeviceAssignment(assignmentId));
    }

    /**
     * Resolved assignment with calibration posture (OK / DEGRADED / UNVERIFIED /
     * UNTRACKED) — the only lane carrying posture, feeding the command
     * workspace's honest device badges. Asset-registry problems arrive stamped
     * on the answer (UNVERIFIED), never masked as OK.
     */
    @GetMapping("/device-assignments/by-device/{deviceRef}")
    public ResponseEntity<String> resolveDeviceAssignment(
            @PathVariable String deviceRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("assignment resolve", reqId, correlationId,
                () -> telemonitoringClient.resolveDeviceAssignment(deviceRef));
    }

    // ── Readings (OF-B25 read surface — newest first, paged; stamps never hidden) ──

    @GetMapping("/readings/by-plan/{planId}")
    public ResponseEntity<String> readingsByPlan(
            @PathVariable String planId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("readings by plan", reqId, correlationId,
                () -> telemonitoringClient.readingsByPlan(planId, page, size));
    }

    @GetMapping("/readings/by-device/{deviceRef}")
    public ResponseEntity<String> readingsByDevice(
            @PathVariable String deviceRef,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("readings by device", reqId, correlationId,
                () -> telemonitoringClient.readingsByDevice(deviceRef, page, size));
    }

    // ── Alert episodes (OF-B26 — §14.7 ladder actions, §14.6 accountable closure) ──

    @GetMapping("/alert-episodes")
    public ResponseEntity<String> listAlertEpisodes(
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceRef,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("episode listing", reqId, correlationId,
                () -> telemonitoringClient.listAlertEpisodes(planId, status, deviceRef));
    }

    @GetMapping("/alert-episodes/{episodeId}")
    public ResponseEntity<String> getAlertEpisode(
            @PathVariable String episodeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("episode fetch", reqId, correlationId,
                () -> telemonitoringClient.getAlertEpisode(episodeId));
    }

    @PostMapping("/alert-episodes/{episodeId}/acknowledge")
    public ResponseEntity<String> acknowledgeEpisode(
            @PathVariable String episodeId,
            @RequestBody(required = false) String body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("episode acknowledge", reqId, correlationId,
                () -> telemonitoringClient.acknowledgeEpisode(episodeId, body, idempotencyKey));
    }

    @PostMapping("/alert-episodes/{episodeId}/start")
    public ResponseEntity<String> startEpisodeProgress(
            @PathVariable String episodeId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("episode start", reqId, correlationId,
                () -> telemonitoringClient.startEpisodeProgress(episodeId, idempotencyKey));
    }

    /** §14.7 ladder rung recording ({@code {rung, note}}) — actor from trust context. */
    @PostMapping(value = "/alert-episodes/{episodeId}/ladder-steps", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordLadderStep(
            @PathVariable String episodeId,
            @RequestBody String body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("ladder step", reqId, correlationId,
                () -> telemonitoringClient.recordLadderStep(episodeId, body, idempotencyKey));
    }

    /** Rung 14–15 handover truth — unresolved handovers are a named §22 failure mode. */
    @PostMapping(value = "/alert-episodes/{episodeId}/assumption-of-care", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recordAssumptionOfCare(
            @PathVariable String episodeId,
            @RequestBody String body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("assumption of care", reqId, correlationId,
                () -> telemonitoringClient.recordAssumptionOfCare(episodeId, body, idempotencyKey));
    }

    /**
     * §14.6 accountable closure. The upstream refuses closure without reviewer
     * + assessment + action + outcome — that refusal passes through with its
     * real code so the form can hold the line honestly.
     */
    @PostMapping("/alert-episodes/{episodeId}/resolve")
    public ResponseEntity<String> resolveEpisode(
            @PathVariable String episodeId,
            @RequestBody(required = false) String body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String reqId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return forward("episode resolve", reqId, correlationId,
                () -> telemonitoringClient.resolveEpisode(episodeId, body, idempotencyKey));
    }

    // ── passthrough plumbing (mirrors MarketplaceRequestBffController) ──────

    private ResponseEntity<String> forward(
            String action, String requestId, String correlationId, UpstreamCall call) {
        try {
            return withMeta(call.run(), requestId, correlationId);
        } catch (HttpStatusCodeException upstream) {
            // Coded-envelope passthrough: telemonitoring domain rejections carry
            // {error:{code,message},timestamp} — forward status + body verbatim.
            log.warn("Telemonitoring {} rejected upstream status={}", action, upstream.getStatusCode());
            MediaType contentType = upstream.getResponseHeaders() != null
                    ? upstream.getResponseHeaders().getContentType()
                    : null;
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body(upstream.getResponseBodyAsString());
        } catch (RestClientException transport) {
            // Transport failure only — never a masked domain outcome.
            log.warn("Telemonitoring {} transport failure: {}", action, transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CompanionHeaders.REQUEST_ID, requestId)
                    .header(CompanionHeaders.CORRELATION_ID, correlationId)
                    .body("{\"error\":{\"code\":\"TELEMONITORING_UNAVAILABLE\","
                            + "\"message\":\"The remote-monitoring service is temporarily unavailable. Please try again shortly.\"},"
                            + "\"status\":502}");
        }
    }

    private ResponseEntity<String> withMeta(
            ResponseEntity<String> upstream, String requestId, String correlationId) {
        MediaType contentType = upstream.getHeaders().getContentType();
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getBody());
    }

    @FunctionalInterface
    private interface UpstreamCall {
        ResponseEntity<String> run();
    }
}
