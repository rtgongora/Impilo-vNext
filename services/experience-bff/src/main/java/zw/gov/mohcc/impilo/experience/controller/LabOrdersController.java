package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.LabOrder;
import zw.gov.mohcc.impilo.experience.repository.LabOrderRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lab order management endpoints.
 * GET  /internal/v1/lab-orders?patient_id= — list orders for patient (paged).
 * GET  /internal/v1/lab-orders?status= — list orders by status (paged).
 * GET  /internal/v1/lab-orders/{id} — get single order.
 * POST /internal/v1/lab-orders — create lab order.
 * POST /internal/v1/lab-orders/{id}/collect — mark as collected.
 * POST /internal/v1/lab-orders/{id}/result — mark as resulted.
 */
@RestController
@RequestMapping("/internal/v1/lab-orders")
public class LabOrdersController {

    private final LabOrderRepository labOrderRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public LabOrdersController(LabOrderRepository labOrderRepository,
                               OutboxService outboxService,
                               JdbcTemplate jdbcTemplate) {
        this.labOrderRepository = labOrderRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateLabOrderRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String test_name,
            String test_code,
            String category,
            String priority,
            String clinical_notes,
            @NotBlank String ordered_by,
            String ordered_by_name,
            String facility_id
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "status") String status) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<LabOrder> result;
        if (patientId != null) {
            result = labOrderRepository.findByTenantIdAndPatientId(tenantId, UUID.fromString(patientId), pageable);
        } else if (status != null) {
            result = labOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            result = labOrderRepository.findAll(pageable);
        }

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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        LabOrder order = labOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab order not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(order));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createLabOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateLabOrderRequest request) {

        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String orderNumber = "LAB-" + now.toInstant().toEpochMilli();

        jdbcTemplate.update("""
            INSERT INTO lab_orders
                (id, tenant_id, patient_id, encounter_id, order_number,
                 test_name, test_code, category, priority, status,
                 clinical_notes, ordered_by, ordered_by_name, facility_id,
                 created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, 'ORDERED', ?, ?, ?, ?::uuid, ?, ?)
            """,
                orderId, tenantId, request.patient_id(), request.encounter_id(),
                orderNumber,
                request.test_name(), request.test_code(), request.category(),
                request.priority() != null ? request.priority() : "ROUTINE",
                request.clinical_notes(),
                request.ordered_by(), request.ordered_by_name(), request.facility_id(),
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.lab-order.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                orderId.toString(),
                Map.of(
                        "order_id", orderId.toString(),
                        "patient_id", request.patient_id(),
                        "test_name", request.test_name(),
                        "test_code", request.test_code() != null ? request.test_code() : "",
                        "ordered_by", request.ordered_by(),
                        "status", "ORDERED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("order_number", orderNumber);
        attributes.put("test_name", request.test_name());
        attributes.put("test_code", request.test_code());
        attributes.put("category", request.category());
        attributes.put("priority", request.priority() != null ? request.priority() : "ROUTINE");
        attributes.put("status", "ORDERED");
        attributes.put("clinical_notes", request.clinical_notes());
        attributes.put("ordered_by", request.ordered_by());
        attributes.put("ordered_by_name", request.ordered_by_name());
        attributes.put("facility_id", request.facility_id());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", orderId.toString(),
                "type", "LabOrder",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/collect")
    @Transactional
    public ResponseEntity<Map<String, Object>> collectLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        LabOrder order = labOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab order not found: " + id));

        order.collect();
        labOrderRepository.save(order);

        outboxService.writeOutboxEvent(
                "impilo.experience.lab-order.collected.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                id.toString(),
                Map.of(
                        "order_id", id.toString(),
                        "patient_id", order.getPatientId().toString(),
                        "status", "COLLECTED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(order));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/result")
    @Transactional
    public ResponseEntity<Map<String, Object>> resultLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        LabOrder order = labOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lab order not found: " + id));

        order.result();
        labOrderRepository.save(order);

        outboxService.writeOutboxEvent(
                "impilo.experience.lab-order.resulted.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "LabOrder",
                id.toString(),
                Map.of(
                        "order_id", id.toString(),
                        "patient_id", order.getPatientId().toString(),
                        "status", "RESULTED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(order));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(LabOrder o) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", o.getPatientId());
        attributes.put("encounter_id", o.getEncounterId());
        attributes.put("order_number", o.getOrderNumber());
        attributes.put("test_name", o.getTestName());
        attributes.put("test_code", o.getTestCode());
        attributes.put("category", o.getCategory());
        attributes.put("priority", o.getPriority());
        attributes.put("status", o.getStatus());
        attributes.put("clinical_notes", o.getClinicalNotes());
        attributes.put("ordered_by", o.getOrderedBy());
        attributes.put("ordered_by_name", o.getOrderedByName());
        attributes.put("facility_id", o.getFacilityId());
        attributes.put("collected_at", o.getCollectedAt());
        attributes.put("resulted_at", o.getResultedAt());
        attributes.put("created_at", o.getCreatedAt());
        attributes.put("updated_at", o.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", o.getId().toString());
        resource.put("type", "LabOrder");
        resource.put("attributes", attributes);
        return resource;
    }
}
