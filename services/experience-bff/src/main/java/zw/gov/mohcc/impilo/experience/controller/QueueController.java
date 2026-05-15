package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;
import zw.gov.mohcc.impilo.experience.queue.QueueStatsAggregator;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Queue management — pure proxy to PCT queue and queue-item APIs.
 */
@RestController
@RequestMapping("/internal/v1/queue")
public class QueueController {

    private final PctServiceClient pctClient;
    private final TshepoAuditServiceClient tshepoAuditServiceClient;
    @Value("${impilo.bff.queue.allow-local-fallback:false}")
    private boolean allowLocalFallback;

    public QueueController(PctServiceClient pctClient, TshepoAuditServiceClient tshepoAuditServiceClient) {
        this.pctClient = pctClient;
        this.tshepoAuditServiceClient = tshepoAuditServiceClient;
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
        if ((queueId == null || queueId.isBlank()) && (facilityId == null || facilityId.isBlank())) {
            return validation("facility_id or queue_id is required");
        }

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
            if (!allowLocalFallback) {
                return upstreamUnavailable("PCT_UNAVAILABLE",
                        "Queue entries are currently unavailable because pct-service could not be reached",
                        requestId, correlationId);
            }
        }

        if (!allowLocalFallback) {
            return upstreamUnavailable("PCT_UNAVAILABLE",
                    "Queue entries are currently unavailable because pct-service returned no payload",
                    requestId, correlationId);
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
            if (!allowLocalFallback) {
                return upstreamUnavailable("PCT_UNAVAILABLE",
                        "Queue entry could not be created because pct-service is unavailable",
                        requestId, correlationId);
            }
        }

        if (!allowLocalFallback) {
            return upstreamUnavailable("PCT_UNAVAILABLE",
                    "Queue entry could not be created because pct-service did not return a queue item",
                    requestId, correlationId);
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

    private void publishQueueAuditToTshepo(String entryId, String logicalAction, String toStatus, String detail) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest req = attrs.getRequest();
        String tenantHeader = req.getHeader(CompanionHeaders.TENANT_ID);
        String corrHeader = req.getHeader(CompanionHeaders.CORRELATION_ID);
        String actorId = req.getHeader(CompanionHeaders.ACTOR_ID);
        String actorType = req.getHeader(CompanionHeaders.ACTOR_TYPE);
        String purpose = req.getHeader(CompanionHeaders.PURPOSE_OF_USE);
        String facilityHeader = req.getHeader(CompanionHeaders.FACILITY_ID);

        UUID tenantId = ImagingGovernanceService.parseTenantUuid(tenantHeader);
        UUID correlationId = parseCorrelationUuid(corrHeader);

        String actionField = toStatus != null && !toStatus.isBlank() ? toStatus : logicalAction;
        if (actionField.length() > 64) {
            actionField = actionField.substring(0, 64);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("eventType", "QUEUE_STATE_CHANGE");
        body.put("actorId", actorId != null && !actorId.isBlank() ? actorId : "SYSTEM");
        body.put("actorType", actorType != null && !actorType.isBlank() ? actorType : "SERVICE");
        body.put("subjectRef", "queue-entry:" + entryId);
        body.put("resourceType", "QUEUE_ENTRY");
        body.put("resourceId", entryId.length() > 255 ? entryId.substring(0, 255) : entryId);
        body.put("action", actionField);
        body.put("outcome", "SUCCESS");
        body.put("purposeOfUse", purpose != null && !purpose.isBlank() ? purpose : "OPERATIONS");
        body.put("correlationId", correlationId);
        if (facilityHeader != null && !facilityHeader.isBlank()) {
            try {
                body.put("facilityId", UUID.fromString(facilityHeader.trim()));
            } catch (IllegalArgumentException ignored) {
                // omit non-UUID facility keys
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("logical_action", logicalAction);
        d.put("to_status", toStatus);
        if (detail != null && !detail.isBlank()) {
            d.put("note", detail);
        }
        if (!d.isEmpty()) {
            body.put("detail", d);
        }

        tshepoAuditServiceClient.ingestAuditEvent(body);
    }

    private static UUID parseCorrelationUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/entries/{id}/audit-trail")
    public ResponseEntity<Map<String, Object>> queueEntryAuditTrail(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        List<Map<String, Object>> rows = List.of();
        String source = "TSHEPO_AUDIT";
        try {
            JsonNode page = tshepoAuditServiceClient.queryEvents(
                    null, "queue-entry:" + id, "QUEUE_STATE_CHANGE", null, null, 0, 200);
            rows = mapTshepoEventsToQueueTrail(page, id);
        } catch (Exception e) {
            log.debug("Tshepo audit query failed for queue entry {}: {}", id, e.getMessage());
            source = "UNAVAILABLE";
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.put("source", source);
        return ResponseEntity.ok(Map.of("data", rows, "meta", meta));
    }

    private static List<Map<String, Object>> mapTshepoEventsToQueueTrail(JsonNode data, String entryId) {
        if (data == null || data.isNull()) {
            return List.of();
        }
        JsonNode items = data.path("items");
        if (!items.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode ev : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ts", ev.path("createdAt").asText(""));
            row.put("queue_entry_id", entryId);
            row.put("action", ev.path("eventType").asText(""));
            row.put("to_status", ev.path("action").asText(""));
            row.put("detail", ev.path("detail").asText(""));
            out.add(row);
        }
        return out;
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
            @RequestBody(required = false) Map<String, Object> body) {
        String targetQueueId = body != null ? strVal(body, "target_queue_id", "targetQueueId") : null;
        if (targetQueueId != null && !targetQueueId.isBlank()) {
            try {
                JsonNode data = pctClient.transferQueueItem(UUID.fromString(id), UUID.fromString(targetQueueId.trim()));
                publishQueueAuditToTshepo(id, "TRANSFER", "TRANSFERRED", "target_queue_id=" + targetQueueId);
                if (data != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", data,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception e) {
                log.debug("PCT transfer failed for queue item {}: {}", id, e.getMessage());
            }
        }
        return updateEntryStatus(id, "TRANSFERRED", requestId, correlationId,
                targetQueueId != null ? "fallback_transfer_target=" + targetQueueId : "fallback_transfer");
    }

    /**
     * Voluntary leave / left without completing service — maps to PCT {@code LEFT} when upstream is available.
     */
    @PostMapping("/entries/{id}/abandon")
    public ResponseEntity<Map<String, Object>> abandonEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return updateEntryStatus(id, "LEFT", requestId, correlationId, "abandon");
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
        return updateEntryStatus(id, newStatus, requestId, correlationId, null);
    }

    private ResponseEntity<Map<String, Object>> updateEntryStatus(
            String id, String newStatus, String requestId, String correlationId, String auditDetail) {
        // Try PCT first
        try {
            UUID uuid = UUID.fromString(id);
            JsonNode data = pctClient.updateQueueItemStatus(uuid, newStatus);
            if (data != null) {
                publishQueueAuditToTshepo(id, "STATUS", newStatus, auditDetail);
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.debug("PCT unavailable for queue status update: {}", e.getMessage());
            if (!allowLocalFallback) {
                return upstreamUnavailable("PCT_UNAVAILABLE",
                        "Queue status update is unavailable because pct-service could not be reached",
                        requestId, correlationId);
            }
        }

        if (!allowLocalFallback) {
            return upstreamUnavailable("PCT_UNAVAILABLE",
                    "Queue status update is unavailable because pct-service returned no payload",
                    requestId, correlationId);
        }

        // Fallback: update local entry
        for (Map<String, Object> entry : LOCAL_QUEUE) {
            if (id.equals(entry.get("id"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attrs = (Map<String, Object>) entry.get("attributes");
                attrs.put("status", newStatus);
                attrs.put("updatedAt", OffsetDateTime.now().toString());
                publishQueueAuditToTshepo(id, "STATUS_LOCAL", newStatus, auditDetail);
                return ResponseEntity.ok(Map.of(
                        "data", entry,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", Map.of("code", "QUEUE_ENTRY_NOT_FOUND", "message", "Queue entry was not found"),
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
            if (!allowLocalFallback) {
                return upstreamUnavailable("PCT_UNAVAILABLE",
                        "Queue definitions are unavailable because pct-service could not be reached",
                        requestId, correlationId);
            }
        }

        if (!allowLocalFallback) {
            return upstreamUnavailable("PCT_UNAVAILABLE",
                    "Queue definitions are unavailable because pct-service returned no payload",
                    requestId, correlationId);
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
            @RequestBody(required = false) Map<String, Object> body) {
        String facilityId = body != null ? strVal(body, "facilityId", "facility_id") : null;

        if (facilityId != null && !facilityId.isBlank()) {
            try {
                JsonNode queues = pctClient.listQueues(UUID.fromString(facilityId.trim()), null);
                if (queues != null) {
                    Map<String, Object> agg = new LinkedHashMap<>(QueueStatsAggregator.fromPctQueueList(queues));
                    long sum = ((Number) agg.getOrDefault("waiting", 0L)).longValue()
                            + ((Number) agg.getOrDefault("called", 0L)).longValue()
                            + ((Number) agg.getOrDefault("inService", 0L)).longValue()
                            + ((Number) agg.getOrDefault("completed", 0L)).longValue()
                            + ((Number) agg.getOrDefault("noShow", 0L)).longValue();
                    agg.put("total", sum);
                    agg.put("source", "PCT");
                    return ResponseEntity.ok(Map.of(
                            "data", agg,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception e) {
                log.debug("PCT aggregate queue stats failed for facility {}: {}", facilityId, e.getMessage());
                if (!allowLocalFallback) {
                    return upstreamUnavailable("PCT_UNAVAILABLE",
                            "Queue statistics are unavailable because pct-service could not be reached",
                            requestId, correlationId);
                }
            }
        }

        if (!allowLocalFallback) {
            return upstreamUnavailable("PCT_UNAVAILABLE",
                    "Queue statistics are unavailable because pct-service returned no payload",
                    requestId, correlationId);
        }

        final String fid = facilityId != null ? facilityId.trim() : "";
        List<Map<String, Object>> relevant = LOCAL_QUEUE.stream()
                .filter(e -> fid.isEmpty()
                        || fid.equals(String.valueOf(((Map<?, ?>) e.get("attributes")).get("facilityId"))))
                .collect(Collectors.toList());

        long waiting = relevant.stream()
                .filter(e -> "WAITING".equals(((Map<?, ?>) e.get("attributes")).get("status")))
                .count();
        long called = relevant.stream()
                .filter(e -> "CALLED".equals(((Map<?, ?>) e.get("attributes")).get("status")))
                .count();
        long inService = relevant.stream()
                .filter(e -> {
                    Object s = ((Map<?, ?>) e.get("attributes")).get("status");
                    return "IN_SERVICE".equals(s) || "IN_PROGRESS".equals(s) || "SEEN".equals(s);
                })
                .count();
        long completed = relevant.stream()
                .filter(e -> "COMPLETED".equals(((Map<?, ?>) e.get("attributes")).get("status")))
                .count();
        long noShow = relevant.stream()
                .filter(e -> "NO_SHOW".equals(((Map<?, ?>) e.get("attributes")).get("status")))
                .count();

        int avgWaitSeconds = 0;
        List<Map<String, Object>> waitingRows = relevant.stream()
                .filter(e -> "WAITING".equals(((Map<?, ?>) e.get("attributes")).get("status")))
                .collect(Collectors.toList());
        if (!waitingRows.isEmpty()) {
            long sumSec = 0;
            for (Map<String, Object> e : waitingRows) {
                Object checked = ((Map<?, ?>) e.get("attributes")).get("checkedInAt");
                if (checked != null) {
                    try {
                        OffsetDateTime t = OffsetDateTime.parse(checked.toString());
                        sumSec += ChronoUnit.SECONDS.between(t, OffsetDateTime.now());
                    } catch (Exception ignored) {
                    }
                }
            }
            avgWaitSeconds = (int) (sumSec / waitingRows.size());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("waiting", waiting);
        data.put("called", called);
        data.put("inService", inService);
        data.put("completed", completed);
        data.put("noShow", noShow);
        data.put("avgWaitSeconds", avgWaitSeconds);
        data.put("total", relevant.size());
        data.put("source", "LOCAL_FALLBACK");

        return ResponseEntity.ok(Map.of(
                "data", data,
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

    private static ResponseEntity<Map<String, Object>> upstreamUnavailable(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static ResponseEntity<Map<String, Object>> validation(String message) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", Map.of("code", "VALIDATION", "message", message)));
    }
}
