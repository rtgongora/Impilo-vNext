package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public record CreateQueueEntryRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String queue_type,
            String priority,
            String reason,
            @NotBlank String patient_cpid,
            String referral_source,
            String referral_id,
            /** When set, enqueue into this queue; otherwise resolved from facility + queue_type. */
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "queue_id or facility_id is required to list queue data from PCT");
        }

        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries")
    public ResponseEntity<Map<String, Object>> createEntry(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateQueueEntryRequest request) {

        UUID facilityUuid = UUID.fromString(request.facility_id().trim());
        JsonNode journeyData = pctClient.startJourney(
                request.patient_cpid().trim(),
                facilityUuid,
                request.referral_source(),
                request.referral_id());
        if (journeyData == null || !journeyData.has("journeyId")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PCT did not return a journeyId");
        }
        String journeyId = journeyData.get("journeyId").asText();

        UUID queueUuid;
        if (request.queue_id() != null && !request.queue_id().isBlank()) {
            queueUuid = UUID.fromString(request.queue_id().trim());
        } else {
            JsonNode queues = pctClient.listQueues(facilityUuid, null);
            queueUuid = findQueueIdByType(queues, request.queue_type());
            if (queueUuid == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No queue found for facility and queue_type=" + request.queue_type());
            }
        }

        int priority = parseQueuePriority(request.priority());
        JsonNode item = pctClient.enqueue(queueUuid, journeyId, priority);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", item != null ? item : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/call")
    public ResponseEntity<Map<String, Object>> callEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        JsonNode data = pctClient.updateQueueItemStatus(id, "CALLED");
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * POST /internal/v1/queue/entries/{id}/triage — delegates triage to PCT for the journey.
     * The client should pass {@code journey_id} or {@code pct_journey_id} in the body (from the queue item).
     */
    @PostMapping("/entries/{id}/triage")
    public ResponseEntity<Map<String, Object>> triageEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        int acuity = body.containsKey("acuity") ? ((Number) body.get("acuity")).intValue() : 3;
        String notes = body.containsKey("notes") ? (String) body.get("notes") : null;

        Object jid = body.get("journey_id");
        if (jid == null) {
            jid = body.get("pct_journey_id");
        }
        if (jid == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "journey_id or pct_journey_id is required for PCT triage");
        }
        String journeyId = jid.toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> vitals = body.get("vitals") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;

        JsonNode triage = pctClient.recordTriage(journeyId, String.valueOf(acuity), vitals, notes);
        return ResponseEntity.ok(Map.of(
                "data", triage != null ? triage : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        JsonNode data = pctClient.updateQueueItemStatus(id, "COMPLETED");
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/no-show")
    public ResponseEntity<Map<String, Object>> markNoShow(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = pctClient.updateQueueItemStatus(id, "NO_SHOW");
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/transfer")
    public ResponseEntity<Map<String, Object>> transferEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        String targetQueue = body.get("target_queue_id");
        if (targetQueue == null || targetQueue.isBlank()) {
            // TODO: wire facility-to-queue resolution when PCT exposes it; targetFacilityId alone is insufficient
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target_queue_id is required for PCT transfer");
        }
        JsonNode data = pctClient.transferQueueItem(id, UUID.fromString(targetQueue.trim()));
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = pctClient.updateQueueItemStatus(id, "PAUSED");
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = pctClient.updateQueueItemStatus(id, "IN_SERVICE");
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/definitions")
    public ResponseEntity<Map<String, Object>> listQueueDefinitions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") UUID facilityId,
            @RequestParam(required = false, name = "workspace_id") UUID workspaceId) {
        if (facilityId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facility_id is required");
        }
        JsonNode data = pctClient.listQueues(facilityId, workspaceId);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        String facilityId = body.get("facilityId");
        if (facilityId == null || facilityId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facilityId is required");
        }
        JsonNode data = pctClient.listQueues(UUID.fromString(facilityId.trim()), null);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
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
