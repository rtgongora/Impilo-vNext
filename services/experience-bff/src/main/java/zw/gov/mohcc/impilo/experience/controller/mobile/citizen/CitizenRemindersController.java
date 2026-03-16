package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen reminders endpoints.
 * GET    /internal/v1/mobile/citizen/reminders          - list reminders
 * POST   /internal/v1/mobile/citizen/reminders          - create reminder
 * PATCH  /internal/v1/mobile/citizen/reminders/{id}     - update reminder (toggle, reschedule)
 * DELETE /internal/v1/mobile/citizen/reminders/{id}     - delete reminder
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/reminders")
public class CitizenRemindersController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenRemindersController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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

        UUID patientId = resolvePatientId(tenantId, actorId);
        int limit = Math.min(size, 100);
        int offset = page * limit;

        String sql = """
            SELECT id, title, reminder_type, description, scheduled_at, recurrence,
                   enabled, source_type, source_id, created_at, updated_at
            FROM reminders WHERE tenant_id = ? AND patient_id = ?
            ORDER BY scheduled_at DESC LIMIT ? OFFSET ?
            """;
        String countSql = "SELECT count(*) FROM reminders WHERE tenant_id = ? AND patient_id = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, patientId, limit, offset);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(this::toResource).toList();
        return ResponseEntity.ok(buildPagedResponse(data, requestId, correlationId, page, limit, total));
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

        UUID patientId = resolvePatientId(tenantId, actorId);
        UUID reminderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String reminderType = body.reminderType() != null ? body.reminderType() : "GENERAL";
        String recurrence = body.recurrence() != null ? body.recurrence() : "ONCE";

        jdbcTemplate.update("""
            INSERT INTO reminders
                (id, tenant_id, patient_id, title, reminder_type, description,
                 scheduled_at, recurrence, enabled, source_type, source_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)
            """, reminderId, tenantId, patientId, body.title(), reminderType, body.description(),
                body.scheduledAt(), recurrence, body.sourceType(), body.sourceId(), now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.reminder-created.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Reminder", reminderId.toString(),
                Map.of("reminder_id", reminderId.toString(), "patient_id", patientId.toString(),
                       "title", body.title(), "scheduled_at", body.scheduledAt().toString()),
                Map.of());

        Map<String, Object> reminder = new LinkedHashMap<>();
        reminder.put("id", reminderId.toString());
        reminder.put("title", body.title());
        reminder.put("reminderType", reminderType);
        reminder.put("description", body.description());
        reminder.put("scheduledAt", body.scheduledAt());
        reminder.put("recurrence", recurrence);
        reminder.put("enabled", true);
        reminder.put("sourceType", body.sourceType());
        reminder.put("sourceId", body.sourceId() != null ? body.sourceId().toString() : null);
        reminder.put("createdAt", now);
        reminder.put("updatedAt", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", reminder);
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

        UUID patientId = resolvePatientId(tenantId, actorId);
        OffsetDateTime now = OffsetDateTime.now();

        // Verify reminder exists and belongs to this patient
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM reminders WHERE id = ? AND tenant_id = ? AND patient_id = ?",
                id, tenantId, patientId);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("Reminder not found: " + id);
        }

        // Build dynamic update
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (body.containsKey("enabled")) {
            setClauses.add("enabled = ?");
            params.add(Boolean.valueOf(body.get("enabled").toString()));
        }
        if (body.containsKey("scheduledAt")) {
            setClauses.add("scheduled_at = ?");
            params.add(OffsetDateTime.parse(body.get("scheduledAt").toString()));
        }
        if (body.containsKey("title")) {
            setClauses.add("title = ?");
            params.add(body.get("title").toString());
        }
        if (body.containsKey("description")) {
            setClauses.add("description = ?");
            params.add(body.get("description") != null ? body.get("description").toString() : null);
        }
        if (body.containsKey("recurrence")) {
            setClauses.add("recurrence = ?");
            params.add(body.get("recurrence").toString());
        }

        setClauses.add("updated_at = ?");
        params.add(now);
        params.add(id);
        params.add(tenantId);

        String updateSql = "UPDATE reminders SET " + String.join(", ", setClauses)
                + " WHERE id = ? AND tenant_id = ?";
        jdbcTemplate.update(updateSql, params.toArray());

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.reminder-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Reminder", id.toString(),
                Map.of("reminder_id", id.toString(), "updated_fields", body.keySet().toString()),
                Map.of());

        // Return updated reminder
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, title, reminder_type, description, scheduled_at, recurrence,
                   enabled, source_type, source_id, created_at, updated_at
            FROM reminders WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(rows.get(0)));
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

        UUID patientId = resolvePatientId(tenantId, actorId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM reminders WHERE id = ? AND tenant_id = ? AND patient_id = ?",
                id, tenantId, patientId);

        if (deleted == 0) {
            throw new ResourceNotFoundException("Reminder not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.reminder-deleted.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "Reminder", id.toString(),
                Map.of("reminder_id", id.toString(), "action", "DELETED"),
                Map.of());

        return ResponseEntity.noContent().build();
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", row.get("id").toString());
        r.put("title", row.get("title"));
        r.put("reminderType", row.get("reminder_type"));
        r.put("description", row.get("description"));
        r.put("scheduledAt", row.get("scheduled_at"));
        r.put("recurrence", row.get("recurrence"));
        r.put("enabled", row.get("enabled"));
        r.put("sourceType", row.get("source_type"));
        r.put("sourceId", row.get("source_id") != null ? row.get("source_id").toString() : null);
        r.put("createdAt", row.get("created_at"));
        r.put("updatedAt", row.get("updated_at"));
        return r;
    }

    private Map<String, Object> buildPagedResponse(List<Map<String, Object>> data, String requestId,
            String correlationId, int page, int size, Long total) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page, "size", size,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / size) : 0
                )
        ));
        return response;
    }
}
