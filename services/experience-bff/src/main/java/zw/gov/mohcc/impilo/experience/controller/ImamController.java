package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IMAM treatment episodes. PCT is the system of record; this is composition only.
 *
 * <p>Reads fail loudly. An empty list returned because the upstream call failed reads as "this
 * child is in no nutrition programme", which is a clinical finding and would be a fabricated one —
 * the exact defect that hid three broken verticals in this programme for as long as it did. A
 * failure here is a 502 that says so.</p>
 *
 * <p>Writes pass the upstream status through rather than flattening it. A 409 (already enrolled),
 * a 422 (criteria not met, no override reason) and a 503 (protocol unreachable) each need a
 * different response from the clinician, and collapsing them into one error hides the difference.</p>
 */
@RestController
@RequestMapping("/internal/v1/imam")
public class ImamController {

    private static final Logger log = LoggerFactory.getLogger(ImamController.class);

    private final PctServiceClient pctClient;

    public ImamController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping("/episodes")
    public ResponseEntity<Map<String, Object>> listEpisodes(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        try {
            JsonNode data = pctClient.listImamEpisodes(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT listImamEpisodes failed for patient={}: {}", patientId, e.getMessage());
            return unavailable("imam_episodes_unavailable",
                    "IMAM episodes could not be retrieved. Do not read this as the child being in no "
                            + "nutrition programme.", requestId, correlationId);
        }
    }

    @GetMapping("/episodes/{episodeId}")
    public ResponseEntity<Map<String, Object>> getEpisode(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String episodeId) {
        try {
            JsonNode data = pctClient.getImamEpisode(episodeId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT getImamEpisode failed for episode={}: {}", episodeId, e.getMessage());
            return unavailable("imam_episode_unavailable",
                    "The IMAM episode could not be retrieved.", requestId, correlationId);
        }
    }

    /**
     * Enrols a child, normally straight off the IMNCI assessment that classified them: pass
     * {@code source_classification} and the programme, ration and discharge criteria are resolved
     * from the same governed pack that produced the classification.
     */
    @PostMapping("/episodes")
    public ResponseEntity<Map<String, Object>> enrol(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = pctClient.enrolImamEpisode(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passThrough(e, requestId, correlationId);
        }
    }

    @PostMapping("/episodes/{episodeId}/visits")
    public ResponseEntity<Map<String, Object>> recordVisit(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = pctClient.recordImamVisit(episodeId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", created != null ? created : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passThrough(e, requestId, correlationId);
        }
    }

    @PostMapping("/episodes/{episodeId}/outcome")
    public ResponseEntity<Map<String, Object>> closeOutcome(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode closed = pctClient.closeImamEpisode(episodeId, body);
            return ResponseEntity.ok(Map.of(
                    "data", closed != null ? closed : Map.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (HttpStatusCodeException e) {
            return passThrough(e, requestId, correlationId);
        }
    }

    @GetMapping("/tracing-queue")
    public ResponseEntity<Map<String, Object>> tracingQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id", required = false) String facilityId) {
        try {
            JsonNode data = pctClient.imamTracingQueue(facilityId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT imamTracingQueue failed for facility={}: {}", facilityId, e.getMessage());
            // An empty tracing queue means every enrolled child is up to date. Returning that when
            // the query failed would send a clinic home believing nobody needs a home visit.
            return unavailable("imam_tracing_queue_unavailable",
                    "The defaulter tracing queue could not be retrieved. An empty list here would mean "
                            + "no child is overdue, which has not been established.", requestId, correlationId);
        }
    }

    /** Keeps 409 / 422 / 503 distinguishable at the client, because each needs a different action. */
    private ResponseEntity<Map<String, Object>> passThrough(HttpStatusCodeException e,
                                                            String requestId, String correlationId) {
        log.warn("PCT IMAM write refused with {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "imam_write_refused");
        body.put("status", e.getStatusCode().value());
        body.put("message", e.getResponseBodyAsString());
        body.put("meta", meta(requestId, correlationId));
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    private ResponseEntity<Map<String, Object>> unavailable(String error, String message,
                                                            String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", error,
                "message", message,
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of("request_id", requestId, "correlation_id", correlationId);
    }
}
