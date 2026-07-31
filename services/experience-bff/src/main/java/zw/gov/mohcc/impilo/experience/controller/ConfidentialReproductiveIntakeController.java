package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Write proxy for pct's confidential reproductive lane (W14-B): reproductive intention,
 * preconception plans, fertility episodes and delivery records.
 *
 * <p>Mounted on {@code /internal/v1/confidential/reproductive} alongside
 * {@link ConfidentialReproductiveReadController} so tshepo-authz classifies the path the client
 * calls. Domain outcomes (409 duplicate delivery, 422 clinical refusal) pass through verbatim;
 * only transport failure becomes a 502.
 */
@RestController
@RequestMapping("/internal/v1/confidential/reproductive")
public class ConfidentialReproductiveIntakeController {

    private static final Logger log =
            LoggerFactory.getLogger(ConfidentialReproductiveIntakeController.class);

    private final PctServiceClient pct;
    private final SubjectResolutionService subjectResolution;

    public ConfidentialReproductiveIntakeController(PctServiceClient pct,
                                                    SubjectResolutionService subjectResolution) {
        this.pct = pct;
        this.subjectResolution = subjectResolution;
    }

    @PostMapping("/reproductive-intentions")
    public ResponseEntity<?> recordReproductiveIntention(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        if (!resolveSubjectInto(payload, "subjectCpid", actorId)) {
            return unresolvedSubject(requestId, correlationId);
        }
        return forward(() -> pct.recordReproductiveIntention(payload), "reproductive intention",
                requestId, correlationId);
    }

    @PostMapping("/preconception-plans")
    public ResponseEntity<?> openPreconceptionPlan(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        if (!resolveSubjectInto(payload, "subjectCpid", actorId)) {
            return unresolvedSubject(requestId, correlationId);
        }
        return forward(() -> pct.openPreconceptionPlan(payload), "preconception plan",
                requestId, correlationId);
    }

    @PatchMapping("/preconception-plans/{planId}")
    public ResponseEntity<?> updatePreconceptionPlan(
            @PathVariable UUID planId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        return forward(() -> pct.updatePreconceptionPlan(planId, payload), "preconception plan update",
                requestId, correlationId);
    }

    @PostMapping("/fertility-episodes")
    public ResponseEntity<?> startFertilityEpisode(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        if (!resolveSubjectInto(payload, "subjectCpid", actorId)) {
            return unresolvedSubject(requestId, correlationId);
        }
        return forward(() -> pct.startFertilityEpisode(payload), "fertility episode",
                requestId, correlationId);
    }

    @PostMapping("/delivery-records")
    public ResponseEntity<?> recordDelivery(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = body == null ? new HashMap<>() : new HashMap<>(body);
        if (!resolveSubjectInto(payload, "motherCpid", actorId)) {
            return unresolvedSubject(requestId, correlationId);
        }
        return forward(() -> pct.recordDeliveryRecord(payload), "delivery record",
                requestId, correlationId);
    }

    private boolean resolveSubjectInto(Map<String, Object> payload, String field, String actorId) {
        Object requested = payload.get(field);
        String cpid = resolveSubject(requested == null ? null : String.valueOf(requested), actorId);
        if (cpid == null) {
            return false;
        }
        payload.put(field, cpid);
        return true;
    }

    private String resolveSubject(String requested, String actorId) {
        if (requested != null && !requested.isBlank() && !"me".equalsIgnoreCase(requested)) {
            return requested;
        }
        return actorId == null ? null : subjectResolution.cpidForHealthId(actorId);
    }

    private static ResponseEntity<?> unresolvedSubject(String requestId, String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", "SUBJECT_UNRESOLVED");
        data.put("message", "Could not resolve which person this record is for.");
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    private interface Call {
        ResponseEntity<JsonNode> invoke();
    }

    private ResponseEntity<?> forward(Call call, String action, String requestId,
                                      String correlationId) {
        try {
            ResponseEntity<JsonNode> upstream = call.invoke();
            Map<String, Object> out = new HashMap<>();
            JsonNode body = upstream.getBody();
            out.put("data", body != null && body.has("data") ? body.get("data") : body);
            out.put("meta", meta(requestId, correlationId));
            return ResponseEntity.status(upstream.getStatusCode()).body(out);
        } catch (HttpStatusCodeException upstream) {
            return passthrough(upstream, requestId, correlationId);
        } catch (RestClientException transport) {
            log.warn("Confidential reproductive {} transport failure: {}", action,
                    transport.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of(
                            "code", "PCT_UNAVAILABLE",
                            "message", transport.getMessage() == null
                                    ? "upstream error" : transport.getMessage(),
                            "clinical_note", "The " + action + " could not be reached. Do not treat "
                                    + "this as an absence of records — the record may exist and be "
                                    + "unreadable right now, which is a different fact from there "
                                    + "being nothing to see."),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private ResponseEntity<?> passthrough(HttpStatusCodeException upstream, String requestId,
                                          String correlationId) {
        log.warn("Confidential reproductive write: upstream rejected status={}",
                upstream.getStatusCode());
        return ResponseEntity.status(upstream.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header(CompanionHeaders.REQUEST_ID, requestId)
                .header(CompanionHeaders.CORRELATION_ID, correlationId)
                .body(upstream.getResponseBodyAsString());
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        return meta;
    }
}
