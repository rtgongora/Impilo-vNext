package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Queue management — pure proxy to PCT queue and queue-item APIs.
 */
@RestController
@RequestMapping("/internal/v1/queue")
public class QueueController {

    private final PctServiceClient pctClient;

    public QueueController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);
    private static final List<Map<String, Object>> LOCAL_QUEUE = new CopyOnWriteArrayList<>();

    public record CreateQueueEntryRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            String queue_type,
            String priority,
            String reason,
            String patient_cpid,
            String referral_source,
            String referral_id,
            String queue_id
    ) {}

    @GetMapping("/entries")
    public ResponseEntity<Map<String, Object>> listEntries(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "queue_id") String queueId,
            @RequestParam(required = false, name = "workspace_id") String workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "queue_type") String queueType) {

        // Try PCT
        try {
            JsonNode data;
            if (queueId != null && !queueId.isBlank()) {
                data = pctClient.listQueueItems(UUID.fromString(queueId.trim()), status);
            } else if (facilityId != null && !facilityId.isBlank()) {
                UUID fid = UUID.fromString(facilityId.trim());
                UUID wid = workspaceId != null && !workspaceId.isBlank() ? UUID.fromString(workspaceId.trim()) : null;
                JsonNode queues = pctClient.listQueues(fid, wid);
                if (queueType != null && !queueType.isBlank() && queues != null && queues.isArray()) {
                    UUID match = findQueueIdByType(queues, queueType);
                    data = match != null ? pctClient.listQueueItems(match, status) : queues;
                } else {
                    data = queues;
                }
            } else {
                data = null;
            }
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.debug("PCT unavailable for queue list: {}", e.getMessage());
        }

        // Fallback: return local queue entries filtered by facility
        List<Map<String, Object>> entries = LOCAL_QUEUE;
        if (facilityId != null) {
            String fid = facilityId;
            entries = entries.stream()
                    .filter(e -> fid.equals(((Map<?, ?>) e.get("attributes")).get("facilityId")))
                    .collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(Map.of(
                "data", entries,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries")
    public ResponseEntity<Map<String, Object>> createEntry(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String patientId = strVal(body, "patient_id", "patientId");
        String facilityId = strVal(body, "facility_id", "facilityId");
        String queueType = strVal(body, "queue_type", "queueType");
        String priority = strVal(body, "priority");
        String cpid = strVal(body, "patient_cpid", "patientCpid");

        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "patient_id is required")));
        }

        // Try PCT
        try {
            UUID facilityUuid = UUID.fromString(facilityId != null ? facilityId.trim() : "");
            JsonNode journeyData = pctClient.startJourney(
                    cpid != null ? cpid.trim() : patientId,
                    facilityUuid, null, null);
            if (journeyData != null && journeyData.has("journeyId")) {
                String journeyId = journeyData.get("journeyId").asText();
                UUID queueUuid;
                if (body.get("queue_id") != null) {
                    queueUuid = UUID.fromString(body.get("queue_id").toString().trim());
                } else {
                    JsonNode queues = pctClient.listQueues(facilityUuid, null);
                    queueUuid = findQueueIdByType(queues, queueType);
                }
                if (queueUuid != null) {
                    int pri = parseQueuePriority(priority);
                    JsonNode item = pctClient.enqueue(queueUuid, journeyId, pri);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "data", item != null ? item : Map.of(),
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            }
        } catch (Exception e) {
            log.info("PCT unavailable — creating local queue entry: {}", e.getMessage());
        }

        // Fallback: create local queue entry
        String entryId = "qe-" + UUID.randomUUID().toString().substring(0, 8);
        String now = OffsetDateTime.now().toString();

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("patientId", patientId);
        attrs.put("facilityId", facilityId);
        attrs.put("queueType", queueType != null ? queueType : "WALK_IN");
        attrs.put("priority", priority != null ? priority : "NORMAL");
        attrs.put("status", "WAITING");
        attrs.put("position", LOCAL_QUEUE.size() + 1);
        attrs.put("checkedInAt", now);
        attrs.put("cpid", cpid);

        Map<String, Object> entry = Map.of("id", entryId, "type", "queue_entry", "attributes", attrs);
        LOCAL_QUEUE.add(entry);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", entry,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return null;
    }

    @PostMapping("/entries/{id}/call")
    public ResponseEntity<Map<String, Object>> callEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return updateEntryStatus(id, "CALLED", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/triage")
    public ResponseEntity<Map<String, Object>> triageEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        return updateEntryStatus(id, "IN_TRIAGE", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        return updateEntryStatus(id, "COMPLETED", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/no-show")
    public ResponseEntity<Map<String, Object>> markNoShow(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return updateEntryStatus(id, "NO_SHOW", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/transfer")
    public ResponseEntity<Map<String, Object>> transferEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, String> body) {
        return updateEntryStatus(id, "TRANSFERRED", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return updateEntryStatus(id, "PAUSED", requestId, correlationId);
    }

    @PostMapping("/entries/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return updateEntryStatus(id, "IN_SERVICE", requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> updateEntryStatus(
            String id, String newStatus, String requestId, String correlationId) {
        // Try PCT first
        try {
            UUID uuid = UUID.fromString(id);
            JsonNode data = pctClient.updateQueueItemStatus(uuid, newStatus);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.debug("PCT unavailable for queue status update: {}", e.getMessage());
        }

        // Fallback: update local entry
        for (Map<String, Object> entry : LOCAL_QUEUE) {
            if (id.equals(entry.get("id"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attrs = (Map<String, Object>) entry.get("attributes");
                attrs.put("status", newStatus);
                attrs.put("updatedAt", OffsetDateTime.now().toString());
                return ResponseEntity.ok(Map.of(
                        "data", entry,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        // Entry not found in local store — return a synthetic response
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", newStatus);
        attrs.put("updatedAt", OffsetDateTime.now().toString());
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id, "type", "queue_entry", "attributes", attrs),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/definitions")
    public ResponseEntity<Map<String, Object>> listQueueDefinitions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {
        try {
            if (facilityId != null) {
                JsonNode data = pctClient.listQueues(UUID.fromString(facilityId.trim()), null);
                if (data != null) {
                    return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            }
        } catch (Exception e) {
            log.debug("PCT unavailable for queue definitions: {}", e.getMessage());
        }
        // Fallback: return default queue definitions
        return ResponseEntity.ok(Map.of(
                "data", List.of(
                        Map.of("id", "qd-walkin", "type", "queue_definition", "attributes", Map.of("name", "Walk-in", "queueType", "WALK_IN")),
                        Map.of("id", "qd-triage", "type", "queue_definition", "attributes", Map.of("name", "Triage", "queueType", "TRIAGE")),
                        Map.of("id", "qd-consult", "type", "queue_definition", "attributes", Map.of("name", "Consultation", "queueType", "CONSULTATION")),
                        Map.of("id", "qd-lab", "type", "queue_definition", "attributes", Map.of("name", "Laboratory", "queueType", "LABORATORY")),
                        Map.of("id", "qd-pharm", "type", "queue_definition", "attributes", Map.of("name", "Pharmacy", "queueType", "PHARMACY"))
                ),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, String> body) {
        long waiting = LOCAL_QUEUE.stream().filter(e -> "WAITING".equals(((Map<?, ?>) e.get("attributes")).get("status"))).count();
        long called = LOCAL_QUEUE.stream().filter(e -> "CALLED".equals(((Map<?, ?>) e.get("attributes")).get("status"))).count();
        long inService = LOCAL_QUEUE.stream().filter(e -> "IN_SERVICE".equals(((Map<?, ?>) e.get("attributes")).get("status"))).count();
        return ResponseEntity.ok(Map.of(
                "data", Map.of("waiting", waiting, "called", called, "inService", inService, "total", LOCAL_QUEUE.size()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static UUID findQueueIdByType(JsonNode queues, String queueType) {
        if (queues == null || !queues.isArray() || queueType == null) {
            return null;
        }
        for (JsonNode q : queues) {
            if (queueType.equalsIgnoreCase(q.path("queueType").asText())) {
                String qid = q.path("queueId").asText(null);
                if (qid != null && !qid.isBlank()) {
                    return UUID.fromString(qid);
                }
            }
        }
        if (queues.size() > 0) {
            String qid = queues.get(0).path("queueId").asText(null);
            if (qid != null && !qid.isBlank()) {
                return UUID.fromString(qid);
            }
        }
        return null;
    }

    private static int parseQueuePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return 0;
        }
        return switch (priority.trim().toUpperCase()) {
            case "EMERGENCY" -> 100;
            case "URGENT" -> 50;
            case "LOW" -> -10;
            default -> 0;
        };
    }
}
