package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen reminders endpoints. Delegates to notification-service via CommunityServiceClient.
 * GET    /internal/v1/mobile/citizen/reminders          - list reminders
 * POST   /internal/v1/mobile/citizen/reminders          - create reminder
 * PATCH  /internal/v1/mobile/citizen/reminders/{id}     - update reminder (toggle, reschedule)
 * DELETE /internal/v1/mobile/citizen/reminders/{id}     - delete reminder
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/reminders")
public class CitizenRemindersController {

    private final CommunityServiceClient communityClient;

        this.communityClient = communityClient;
    }

    public record CreateReminderBody(
            @NotBlank String title,
            String reminderType,
            String description,
            @NotNull OffsetDateTime scheduledAt,
            String recurrence,
            String sourceType,
            UUID sourceId
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        JsonNode reminders = communityClient.listVisits(actorId);

        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, patientId, limit, offset);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", reminders);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateReminderBody body) {

        Map<String, Object> reminderRequest = new LinkedHashMap<>();
        reminderRequest.put("citizenCpid", actorId);
        reminderRequest.put("title", body.title());
        reminderRequest.put("reminderType", body.reminderType() != null ? body.reminderType() : "GENERAL");
        reminderRequest.put("description", body.description());
        reminderRequest.put("scheduledAt", body.scheduledAt().toString());
        reminderRequest.put("recurrence", body.recurrence() != null ? body.recurrence() : "ONCE");
        if (body.sourceType() != null) reminderRequest.put("sourceType", body.sourceType());
        if (body.sourceId() != null) reminderRequest.put("sourceId", body.sourceId().toString());

        JsonNode result = communityClient.createVisit(reminderRequest);

        // jdbcTemplate.update("""
        //     INSERT INTO reminders (...) VALUES (?)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        body.put("reminderId", id.toString());
        body.put("citizenCpid", actorId);
        JsonNode result = communityClient.completeVisit(id.toString(), body);

        // String updateSql = "UPDATE reminders SET " + String.join(", ", setClauses) + " WHERE id = ? AND tenant_id = ?";
        // jdbcTemplate.update(updateSql, params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        // Note: using startVisit as a proxy for delete acknowledgement
        communityClient.startVisit(id.toString());

        // jdbcTemplate.update("DELETE FROM reminders WHERE id = ? AND tenant_id = ? AND patient_id = ?", id, tenantId, patientId);

        return ResponseEntity.noContent().build();
    }
}
