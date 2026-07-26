package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Partograph experience API — proxies the canonical PCT labour-progress surface.
 *
 * <p>PCT owns the record and the assessment: {@code pct.pct_partograph_sessions} plus
 * {@code pct.pct_labour_observations} (one observation table, with a nullable session id), and
 * {@code PartographProgressEngine} decides where a labour sits against the alert and action lines.
 * The BFF composes only — the {@code data}/{@code meta} envelope; trust headers are forwarded by
 * {@code ServiceClientConfig}.
 *
 * <p><b>Why this class exists twice.</b> An earlier version was deleted in {@code 062d827c9} while
 * resolving startup blockers, at a time when the PCT endpoints behind it did not yet exist. They do
 * now, the client methods were never removed, and the shell never stopped calling these paths — so
 * until this controller returned, every partograph route was a 404 and no midwife could open a
 * labour chart in a browser.
 *
 * <p><b>Failure is reported, never rendered as absence.</b> Every proxy returns 502 when PCT cannot
 * be reached or returns nothing. An empty partograph and an unreachable one are different clinical
 * statements, and only one of them means "this labour has not been charted yet". Note that "no open
 * partograph" is <em>not</em> a failure: PCT answers that case explicitly with
 * {@code partograph_active: false}, which is a real answer and passes through as 200.
 */
@RestController
@RequestMapping("/internal/v1/maternity/partograph")
public class MaternityPartographController {

    private static final Logger log = LoggerFactory.getLogger(MaternityPartographController.class);

    private final PctServiceClient pctClient;

    public MaternityPartographController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    /** Open a partograph session. PCT enforces one active session per patient. */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.createPartographSession(body), requestId, correlationId, Map.of());
    }

    /**
     * The open partograph for a patient, if there is one.
     *
     * <p>PCT distinguishes "no partograph is open" from "we could not ask" — the first comes back as
     * a 200 carrying {@code partograph_active: false}. This proxy must preserve that distinction and
     * never collapse the second into the first.
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<Map<String, Object>> activeSession(
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getActivePartographSession(patientId, encounterId),
                requestId, correlationId, Map.of("patient_id", patientId));
    }

    /** A session with its plotted points and the current progress assessment. */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getPartographSession(sessionId), requestId, correlationId, Map.of());
    }

    /**
     * Record an observation on the partograph.
     *
     * <p>PCT returns the progress assessment alongside the written point, deliberately: a reading
     * that crosses the action line has to reach the midwife who just recorded it rather than wait
     * for someone to reopen the chart.
     */
    @PostMapping("/sessions/{sessionId}/points")
    public ResponseEntity<Map<String, Object>> addPoint(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.addPartographPoint(sessionId, body), requestId, correlationId, Map.of());
    }

    /**
     * Close the session.
     *
     * <p>Both PATCH and POST are accepted and both forward as PCT's POST. The shell has always sent
     * PATCH here while PCT exposes POST; accepting one verb only would leave a session that reads as
     * closed in the browser and is still open in the record.
     */
    @RequestMapping(path = "/sessions/{sessionId}/close", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> closeSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        return proxy(() -> pctClient.closePartographSession(sessionId, payload),
                requestId, correlationId, Map.of());
    }

    // ------------------------------------------------------------------

    private interface Call {
        JsonNode invoke();
    }

    private ResponseEntity<Map<String, Object>> proxy(Call call, String requestId, String correlationId,
                                                      Map<String, Object> extraMeta) {
        try {
            JsonNode data = call.invoke();
            if (data == null) {
                return upstreamFailure(requestId, correlationId, "No payload returned");
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("request_id", requestId);
            meta.put("correlation_id", correlationId);
            meta.putAll(extraMeta);
            Map<String, Object> out = new HashMap<>();
            out.put("data", data);
            out.put("meta", meta);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("PCT partograph proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String requestId, String correlationId, String msg) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of(
                        "code", "PCT_UNAVAILABLE",
                        "message", msg == null ? "upstream error" : msg,
                        "clinical_note", "The partograph could not be read. Do not treat this as an "
                                + "absence of labour observations."),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
