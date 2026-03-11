package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
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

    private final QueueEntryRepository queueEntryRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public QueueController(QueueEntryRepository queueEntryRepository,
                           OutboxService outboxService,
                           JdbcTemplate jdbcTemplate) {
        this.queueEntryRepository = queueEntryRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateQueueEntryRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String queue_type,
            String priority,
            String reason
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

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

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

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("queue_type", request.queue_type());
        attributes.put("priority", request.priority() != null ? request.priority() : "NORMAL");
        attributes.put("reason", request.reason());
        attributes.put("status", "WAITING");
        attributes.put("arrival_time", now);
        attributes.put("created_at", now);

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
