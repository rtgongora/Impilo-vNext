package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Queue management endpoints.
 * GET  /internal/v1/queue/entries — list queue entries with filters.
 * POST /internal/v1/queue/entries — create queue entry.
 * POST /internal/v1/queue/entries/{id}/call — call patient from queue.
 * POST /internal/v1/queue/entries/{id}/complete — complete queue entry.
 */
@RestController
@RequestMapping("/internal/v1/queue")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private final PctServiceClient pctClient;

    public QueueController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateQueueEntryRequest(
            @NotBlank String patient_id,
            String facility_id,
            @NotBlank String queue_type,
            String priority,
            String reason,
            String patient_cpid,
            String referral_source,
            String referral_id
    ) {}

    @GetMapping("/entries")
    public ResponseEntity<Map<String, Object>> listEntries(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "queue_type") String queueType) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/entries")
    public ResponseEntity<Map<String, Object>> createEntry(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateQueueEntryRequest request) {

        UUID entryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Store patient_cpid for later PCT delegation
        String cpid = request.patient_cpid();
        if (cpid == null || cpid.isBlank()) {
            // Resolve CPID from patients table
            if (!cpidRows.isEmpty() && cpidRows.get(0).get("cpid") != null) {
                cpid = cpidRows.get(0).get("cpid").toString();
            }
        }

        // Delegate to PCT: start a journey so the sovereign service tracks this visit
        String pctJourneyId = null;
        if (cpid != null && !cpid.isBlank()) {
            try {
                JsonNode journeyData = pctClient.startJourney(
                        cpid,
                        UUID.fromString(request.facility_id()),
                        request.referral_source(),
                        request.referral_id());
                if (journeyData != null && journeyData.has("journeyId")) {
                    pctJourneyId = journeyData.get("journeyId").asText();
                }
                log.info("PCT journey started: {} for queue entry {}", pctJourneyId, entryId);

                // Persist the PCT journey reference
            } catch (Exception e) {
                log.warn("PCT journey delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("queue_type", request.queue_type());
        attributes.put("priority", request.priority() != null ? request.priority() : "NORMAL");
        attributes.put("reason", request.reason());
        attributes.put("status", "WAITING");
        attributes.put("arrival_time", now);
        attributes.put("created_at", now);
        if (pctJourneyId != null) {
            attributes.put("pct_journey_id", pctJourneyId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", entryId.toString(),
                "type", "QueueEntry",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/entries/{id}/call")
    public ResponseEntity<Map<String, Object>> callEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    /**
     * Record a triage assessment for a queue entry.
     * POST /internal/v1/queue/entries/{id}/triage
     *
     * Creates a triage record via the triage API and updates the queue entry's
     * triage_category and priority based on acuity level.
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

        // Map acuity to triage category and priority
        String triageCategory = switch (acuity) {
            case 1 -> "RED";
            case 2 -> "ORANGE";
            case 3 -> "YELLOW";
            case 4 -> "GREEN";
            case 5 -> "BLUE";
            default -> "YELLOW";
        };
        String priority = switch (acuity) {
            case 1 -> "EMERGENCY";
            case 2 -> "URGENT";
            case 3 -> "NORMAL";
            case 4, 5 -> "LOW";
            default -> "NORMAL";
        };

        // Update queue entry triage status
        OffsetDateTime now = OffsetDateTime.now();

        // Create triage record via triage API
        UUID triageId = UUID.randomUUID();
        String dangerSignsJson = "[]";
        String vitalsJson = null;
        try {
            if (body.containsKey("danger_signs")) {
                dangerSignsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(body.get("danger_signs"));
            }
            if (body.containsKey("vitals")) {
                vitalsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(body.get("vitals"));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize triage data: {}", e.getMessage());
        }

        String triagedBy = body.containsKey("triaged_by") ? (String) body.get("triaged_by") : "system";
        String triagedByName = body.containsKey("triaged_by_name") ? (String) body.get("triaged_by_name") : "";

        // Delegate to PCT if journey ID available
        String pctJourneyId = null;
        if (!journeyRows.isEmpty() && journeyRows.get(0).get("pct_journey_id") != null) {
            pctJourneyId = journeyRows.get(0).get("pct_journey_id").toString();
            try {
                pctClient.recordTriage(pctJourneyId, String.valueOf(acuity), null, notes);
                // Update triage record with journey ID
                log.info("PCT triage delegated from queue for journey={}", pctJourneyId);
            } catch (Exception e) {
                log.warn("PCT triage delegation from queue failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", id.toString(),
                "triage_id", triageId.toString(),
                "acuity", acuity,
                "triage_category", triageCategory,
                "priority", priority,
                "status", "TRIAGED"
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/entries/{id}/no-show")
    public ResponseEntity<Map<String, Object>> markNoShow(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        OffsetDateTime now = OffsetDateTime.now();
        if (updated == 0) throw new ResourceNotFoundException("Queue entry not found or not in callable state: " + id);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "NO_SHOW"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/transfer")
    public ResponseEntity<Map<String, Object>> transferEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        String targetFacilityId = body.get("targetFacilityId");
        String reason = body.getOrDefault("reason", "");
        OffsetDateTime now = OffsetDateTime.now();

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "TRANSFERRED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        OffsetDateTime now = OffsetDateTime.now();
        if (updated == 0) throw new ResourceNotFoundException("Queue entry not found or not in pausable state: " + id);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "PAUSED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        OffsetDateTime now = OffsetDateTime.now();
        if (updated == 0) throw new ResourceNotFoundException("Queue entry not found or not paused: " + id);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "IN_SERVICE"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/definitions")
    public ResponseEntity<Map<String, Object>> listQueueDefinitions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") UUID facilityId) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/entries/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        String facilityId = body.get("facilityId");

        Map<String, Object> stats = new LinkedHashMap<>();
        if (facilityId != null) {

            long waiting = 0, called = 0, inService = 0, completed = 0, noShow = 0;
            double avgWait = 0;
            for (Map<String, Object> row : counts) {
                String status = row.get("status").toString();
                long count = ((Number) row.get("count")).longValue();
                switch (status) {
                    case "WAITING" -> { waiting = count; avgWait = row.get("avg_wait_seconds") != null ? ((Number) row.get("avg_wait_seconds")).doubleValue() : 0; }
                    case "CALLED" -> called = count;
                    case "IN_SERVICE", "SEEN" -> inService = count;
                    case "COMPLETED" -> completed = count;
                    case "NO_SHOW" -> noShow = count;
                }
            }
            stats.put("waiting", waiting);
            stats.put("called", called);
            stats.put("inService", inService);
            stats.put("completed", completed);
            stats.put("noShow", noShow);
            stats.put("avgWaitSeconds", Math.round(avgWait));
        }

        return ResponseEntity.ok(Map.of("data", stats,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
