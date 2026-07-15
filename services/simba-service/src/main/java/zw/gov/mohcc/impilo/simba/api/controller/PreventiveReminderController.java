package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.PreventiveReminderService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.persistence.entity.PreventiveReminderEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Preventive wellness reminders. Citizen-self read + snooze/complete; scheduling by self/coach/system. */
@RestController
@RequestMapping("/internal/v1/wellness/reminders")
public class PreventiveReminderController {

    private final PreventiveReminderService reminderService;
    private final WellnessAccessGuard guard;

    public PreventiveReminderController(PreventiveReminderService reminderService, WellnessAccessGuard guard) {
        this.reminderService = reminderService;
        this.guard = guard;
    }

    private WellnessAccessGuard.WellnessAccessContext ctx(
            UUID tenantId, String actorId, String actorType, String providerId,
            String correlationId, String requestId) {
        return new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType, providerId,
                null, null, null, correlationId, requestId);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam("person_cpid") String personCpid) {
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, correlationId, requestId),
                personCpid, "wellness.reminder.list");
        return Map.of("data", reminderService.forPerson(tenantId, personCpid));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> schedule(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, correlationId, requestId),
                personCpid, "wellness.reminder.schedule");
        OffsetDateTime dueAt = OffsetDateTime.parse(required(body, "due_at"));
        Integer cadence = body.get("cadence_months") == null ? null
                : Integer.valueOf(String.valueOf(body.get("cadence_months")));
        PreventiveReminderEntity r = reminderService.schedule(tenantId, personCpid,
                str(body, "reminder_type") == null ? "FOLLOW_UP" : str(body, "reminder_type"),
                required(body, "title"), str(body, "detail"), dueAt, cadence,
                str(body, "channel"), str(body, "source_ref"), actorId != null ? actorId : personCpid);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", r));
    }

    @PostMapping("/{reminderId}/snooze")
    public Map<String, Object> snooze(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("reminderId") UUID reminderId,
            @RequestBody Map<String, Object> body) {
        OffsetDateTime until = OffsetDateTime.parse(required(body, "until"));
        PreventiveReminderEntity r = reminderService.snooze(tenantId, reminderId, until);
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, correlationId, requestId),
                r.getPersonCpid(), "wellness.reminder.snooze");
        return Map.of("data", r);
    }

    @PostMapping("/{reminderId}/complete")
    public Map<String, Object> complete(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("reminderId") UUID reminderId) {
        PreventiveReminderEntity r = reminderService.complete(tenantId, reminderId);
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, correlationId, requestId),
                r.getPersonCpid(), "wellness.reminder.complete");
        return Map.of("data", r);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
