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
 * Cardiotocography experience API — proxies the canonical PCT fetal-monitoring surface.
 *
 * <p>PCT owns the trace: {@code pct.pct_ctg_sessions}, {@code pct_ctg_chunks} and
 * {@code pct_ctg_annotations}. A chunk that declares more samples than it carries records the
 * shortfall in {@code missing_sample_count} rather than interpolating, because a flat line the
 * transducer never measured is indistinguishable on screen from one it did.
 *
 * <p><b>Why this class exists twice.</b> Deleted in {@code 062d827c9} while resolving startup
 * blockers. Its replacement at the time was an in-process map that emptied on every pod restart;
 * that is gone, PCT now persists the trace, and this proxy is what lets a browser reach it. Between
 * the deletion and now, every CTG route was a 404.
 *
 * <p><b>Failure is reported, never rendered as absence.</b> Every proxy returns 502 when PCT cannot
 * be reached or returns nothing. An empty trace and an unreachable one are different clinical
 * statements about a fetus being monitored.
 */
@RestController
@RequestMapping("/internal/v1/maternity/ctg")
public class FetalMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(FetalMonitoringController.class);

    private final PctServiceClient pctClient;

    public FetalMonitoringController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    /** Open a monitoring session. PCT enforces one active session per patient. */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.createFetalMonitoringSession(body), requestId, correlationId, Map.of());
    }

    /**
     * The open monitoring session for a patient, if there is one.
     *
     * <p>"Nobody is being monitored right now" is an answer PCT gives explicitly; "we could not ask"
     * is a 502. The two must not collapse into each other.
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<Map<String, Object>> activeSession(
            @RequestParam String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getActiveFetalMonitoringSession(patientId, encounterId),
                requestId, correlationId, Map.of("patient_id", patientId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getFetalMonitoringSession(sessionId), requestId, correlationId, Map.of());
    }

    /** Append a block of samples to the trace. */
    @PostMapping("/sessions/{sessionId}/chunks")
    public ResponseEntity<Map<String, Object>> addChunk(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.addFetalMonitoringChunk(sessionId, body), requestId, correlationId, Map.of());
    }

    /** Read the trace, optionally narrowed to one channel or a time window. */
    @GetMapping("/sessions/{sessionId}/chunks")
    public ResponseEntity<Map<String, Object>> getChunks(
            @PathVariable String sessionId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.getFetalMonitoringChunks(sessionId, channel, from, to),
                requestId, correlationId, Map.of());
    }

    /** Record a clinician annotation against a point on the trace. */
    @PostMapping("/sessions/{sessionId}/annotations")
    public ResponseEntity<Map<String, Object>> addAnnotation(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> pctClient.addFetalMonitoringAnnotation(sessionId, body),
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
            log.warn("PCT fetal-monitoring proxy failed: {}", e.getMessage());
            return upstreamFailure(requestId, correlationId, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String requestId, String correlationId, String msg) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of(
                        "code", "PCT_UNAVAILABLE",
                        "message", msg == null ? "upstream error" : msg,
                        "clinical_note", "The fetal monitoring trace could not be read. Do not treat "
                                + "this as an absence of monitoring."),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
