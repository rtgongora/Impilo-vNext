package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
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
    private final TusoServiceClient tusoClient;

    public ShiftController(ShiftRepository shiftRepository,
                           OutboxService outboxService,
                           JdbcTemplate jdbcTemplate,
                           TusoServiceClient tusoClient) {
        this.shiftRepository = shiftRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.tusoClient = tusoClient;
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

        // STRANGLER: migrated — delegate to TusoServiceClient
        JsonNode shift = tusoClient.getCurrentShift(userId);

        // STRANGLER: migrated — was ShiftRepository.findCurrentShift
        // Shift shift = shiftRepository.findCurrentShift(tenantId, userId)
        //         .orElseThrow(() -> new ResourceNotFoundException("No active shift found for user: " + userId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", shift);
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

        // STRANGLER: migrated — delegate to TusoServiceClient
        Map<String, Object> shiftData = new LinkedHashMap<>();
        shiftData.put("facility_id", request.facility_id());
        shiftData.put("workspace_id", request.workspace_id());
        shiftData.put("user_id", request.user_id());
        shiftData.put("tenant_id", tenantId);

        JsonNode result = tusoClient.startShift(shiftData);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into shifts table
        // jdbcTemplate.update("""
        //     INSERT INTO shifts (...) VALUES (...)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.shift.started.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Shift",
                requestId,
                Map.of(
                        "facility_id", request.facility_id(),
                        "user_id", request.user_id(),
                        "status", "ACTIVE"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        // STRANGLER: migrated — delegate to TusoServiceClient
        Map<String, Object> endData = new LinkedHashMap<>();
        if (request != null && request.handover_notes() != null) {
            endData.put("handover_notes", request.handover_notes());
        }

        JsonNode result = tusoClient.endShift(id.toString(), endData);

        // STRANGLER: migrated — was ShiftRepository.findById + shift.end() + save
        // Shift shift = shiftRepository.findById(id)
        //         .filter(s -> s.getTenantId().equals(tenantId))
        //         .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + id));
        // shift.end(handoverNotes);
        // shiftRepository.save(shift);

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
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
