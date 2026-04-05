package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.domain.QueueEntry;
import zw.gov.mohcc.impilo.experience.repository.QueueEntryRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    private final QueueEntryRepository queueEntryRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final PctServiceClient pctClient;

    public QueueController(QueueEntryRepository queueEntryRepository,
                           OutboxService outboxService,
                           JdbcTemplate jdbcTemplate,
                           PctServiceClient pctClient) {
        this.queueEntryRepository = queueEntryRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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

        // Sort by arrival time ascending (FIFO — longest waiting first)
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Order.asc("arrivalTime")));

        Page<QueueEntry> result = queueEntryRepository.findByFilters(
                tenantId, facilityId, status, queueType, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries")
    @Transactional
    public ResponseEntity<Map<String, Object>> createEntry(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateQueueEntryRequest request) {

        UUID entryId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO queue_entries
                (id, tenant_id, facility_id, patient_id, queue_type, priority, reason, status,
                 arrival_time, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, 'WAITING', ?, ?, ?)
            """,
                entryId, tenantId, request.facility_id(), request.patient_id(),
                request.queue_type(),
                request.priority() != null ? request.priority() : "NORMAL",
                request.reason(),
                now, now, now);

        // Store patient_cpid for later PCT delegation
        String cpid = request.patient_cpid();
        if (cpid == null || cpid.isBlank()) {
            // Resolve CPID from patients table
            List<Map<String, Object>> cpidRows = jdbcTemplate.queryForList(
                    "SELECT cpid FROM patients WHERE id = ?::uuid AND tenant_id = ?",
                    request.patient_id(), tenantId);
            if (!cpidRows.isEmpty() && cpidRows.get(0).get("cpid") != null) {
                cpid = cpidRows.get(0).get("cpid").toString();
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "QueueEntry",
                entryId.toString(),
                Map.of(
                        "queue_entry_id", entryId.toString(),
                        "patient_id", request.patient_id(),
                        "facility_id", request.facility_id(),
                        "queue_type", request.queue_type(),
                        "status", "WAITING"
                ),
                Map.of()
        );

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
                if (pctJourneyId != null) {
                    jdbcTemplate.update(
                            "UPDATE queue_entries SET pct_journey_id = ? WHERE id = ?",
                            pctJourneyId, entryId);
                }
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
    @Transactional
    public ResponseEntity<Map<String, Object>> callEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        QueueEntry entry = queueEntryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + id));

        entry.call();
        queueEntryRepository.save(entry);

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-called.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "QueueEntry",
                id.toString(),
                Map.of(
                        "queue_entry_id", id.toString(),
                        "status", "CALLED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(entry));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Record a triage assessment for a queue entry.
     * POST /internal/v1/queue/entries/{id}/triage
     *
     * Creates a triage record via the triage API and updates the queue entry's
     * triage_category and priority based on acuity level.
     */
    @PostMapping("/entries/{id}/triage")
    @Transactional
    public ResponseEntity<Map<String, Object>> triageEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        QueueEntry entry = queueEntryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + id));

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
        jdbcTemplate.update("""
            UPDATE queue_entries SET triage_category = ?, priority = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, triageCategory, priority, now, id, tenantId);

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

        jdbcTemplate.update("""
            INSERT INTO triage_records
                (id, tenant_id, patient_id, queue_entry_id, pct_journey_id,
                 acuity, chief_complaint, danger_signs, vitals, notes,
                 triaged_by, triaged_by_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
            """,
                triageId, tenantId, entry.getPatientId(), id,
                null, // pct_journey_id resolved below
                acuity,
                body.containsKey("chief_complaint") ? (String) body.get("chief_complaint") : null,
                dangerSignsJson, vitalsJson, notes,
                triagedBy, triagedByName, now, now);

        // Delegate to PCT if journey ID available
        String pctJourneyId = null;
        List<Map<String, Object>> journeyRows = jdbcTemplate.queryForList(
                "SELECT pct_journey_id FROM queue_entries WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (!journeyRows.isEmpty() && journeyRows.get(0).get("pct_journey_id") != null) {
            pctJourneyId = journeyRows.get(0).get("pct_journey_id").toString();
            try {
                pctClient.recordTriage(pctJourneyId, String.valueOf(acuity), null, notes);
                // Update triage record with journey ID
                jdbcTemplate.update("UPDATE triage_records SET pct_journey_id = ? WHERE id = ?",
                        pctJourneyId, triageId);
                log.info("PCT triage delegated from queue for journey={}", pctJourneyId);
            } catch (Exception e) {
                log.warn("PCT triage delegation from queue failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-triaged.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "QueueEntry", id.toString(),
                Map.of(
                        "queue_entry_id", id.toString(),
                        "acuity", acuity,
                        "triage_category", triageCategory,
                        "priority", priority
                ),
                Map.of()
        );

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
    @Transactional
    public ResponseEntity<Map<String, Object>> completeEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        QueueEntry entry = queueEntryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Queue entry not found: " + id));

        entry.complete();
        queueEntryRepository.save(entry);

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-completed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "QueueEntry",
                id.toString(),
                Map.of(
                        "queue_entry_id", id.toString(),
                        "status", "COMPLETED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(entry));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/entries/{id}/no-show")
    @Transactional
    public ResponseEntity<Map<String, Object>> markNoShow(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        OffsetDateTime now = OffsetDateTime.now();
        int updated = jdbcTemplate.update("""
            UPDATE queue_entries SET status = 'NO_SHOW', no_show_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('WAITING', 'CALLED')
            """, now, now, id, tenantId);
        if (updated == 0) throw new ResourceNotFoundException("Queue entry not found or not in callable state: " + id);

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-noshow.v1", correlationId, requestId,
                requestId, tenantId, "", "QueueEntry", id.toString(),
                Map.of("queue_entry_id", id.toString(), "status", "NO_SHOW"), Map.of());

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "NO_SHOW"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/entries/{id}/transfer")
    @Transactional
    public ResponseEntity<Map<String, Object>> transferEntry(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {

        String targetFacilityId = body.get("targetFacilityId");
        String reason = body.getOrDefault("reason", "");
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            UPDATE queue_entries SET status = 'TRANSFERRED', transferred_to = ?::uuid,
                transfer_reason = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ?
            """, targetFacilityId, reason, now, id, tenantId);

        outboxService.writeOutboxEvent(
                "impilo.experience.queue.entry-transferred.v1", correlationId, requestId,
                requestId, tenantId, "", "QueueEntry", id.toString(),
                Map.of("queue_entry_id", id.toString(), "status", "TRANSFERRED",
                        "target_facility_id", targetFacilityId != null ? targetFacilityId : ""),
                Map.of());

        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "status", "TRANSFERRED"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
            List<Map<String, Object>> counts = jdbcTemplate.queryForList("""
                SELECT status, COUNT(*) as count,
                       AVG(EXTRACT(EPOCH FROM (COALESCE(called_at, now()) - arrival_time))) as avg_wait_seconds
                FROM queue_entries
                WHERE tenant_id = ? AND facility_id = ?::uuid
                    AND created_at >= CURRENT_DATE
                GROUP BY status
                """, tenantId, facilityId);

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

    private Map<String, Object> toResource(QueueEntry q) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", q.getFacilityId());
        attributes.put("workspace_id", q.getWorkspaceId());
        attributes.put("patient_id", q.getPatientId());
        attributes.put("queue_type", q.getQueueType());
        attributes.put("priority", q.getPriority());
        attributes.put("status", q.getStatus());
        attributes.put("triage_category", q.getTriageCategory());
        attributes.put("reason", q.getReason());
        attributes.put("notes", q.getNotes());
        attributes.put("assigned_to", q.getAssignedTo());
        attributes.put("arrival_time", q.getArrivalTime());
        attributes.put("called_at", q.getCalledAt());
        attributes.put("seen_at", q.getSeenAt());
        attributes.put("completed_at", q.getCompletedAt());
        attributes.put("created_at", q.getCreatedAt());
        attributes.put("updated_at", q.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", q.getId().toString());
        resource.put("type", "QueueEntry");
        resource.put("attributes", attributes);
        return resource;
    }
}
