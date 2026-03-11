package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Shift;
import zw.gov.mohcc.impilo.experience.repository.ShiftRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Shift management endpoints.
 * GET  /internal/v1/shifts/current — get current active shift for user.
 * POST /internal/v1/shifts/start — start a new shift.
 * POST /internal/v1/shifts/{id}/end — end a shift.
 */
@RestController
@RequestMapping("/internal/v1/shifts")
public class ShiftController {

    private final ShiftRepository shiftRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public ShiftController(ShiftRepository shiftRepository,
                           OutboxService outboxService,
                           JdbcTemplate jdbcTemplate) {
        this.shiftRepository = shiftRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record StartShiftRequest(
            @NotBlank String facility_id,
            String workspace_id,
            @NotBlank String user_id
    ) {}

    public record EndShiftRequest(
            String handover_notes
    ) {}

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "user_id") String userId) {

        Shift shift = shiftRepository.findCurrentShift(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active shift found for user: " + userId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(shift));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/start")
    @Transactional
    public ResponseEntity<Map<String, Object>> startShift(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody StartShiftRequest request) {

        UUID shiftId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO shifts
                (id, tenant_id, facility_id, workspace_id, user_id, status,
                 started_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, 'ACTIVE', ?, ?, ?)
            """,
                shiftId, tenantId, request.facility_id(),
                request.workspace_id(),
                request.user_id(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.shift.started.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Shift",
                shiftId.toString(),
                Map.of(
                        "shift_id", shiftId.toString(),
                        "facility_id", request.facility_id(),
                        "user_id", request.user_id(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("workspace_id", request.workspace_id());
        attributes.put("user_id", request.user_id());
        attributes.put("status", "ACTIVE");
        attributes.put("started_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", shiftId.toString(),
                "type", "Shift",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/end")
    @Transactional
    public ResponseEntity<Map<String, Object>> endShift(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) EndShiftRequest request) {

        Shift shift = shiftRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + id));

        String handoverNotes = request != null ? request.handover_notes() : null;
        shift.end(handoverNotes);
        shiftRepository.save(shift);

        outboxService.writeOutboxEvent(
                "impilo.experience.shift.ended.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Shift",
                id.toString(),
                Map.of(
                        "shift_id", id.toString(),
                        "status", "ENDED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(shift));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(Shift s) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", s.getFacilityId());
        attributes.put("workspace_id", s.getWorkspaceId());
        attributes.put("user_id", s.getUserId());
        attributes.put("status", s.getStatus());
        attributes.put("started_at", s.getStartedAt());
        attributes.put("ended_at", s.getEndedAt());
        attributes.put("handover_notes", s.getHandoverNotes());
        attributes.put("created_at", s.getCreatedAt());
        attributes.put("updated_at", s.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", s.getId().toString());
        resource.put("type", "Shift");
        resource.put("attributes", attributes);
        return resource;
    }
}
