package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.ActivityService;
import zw.gov.mohcc.impilo.simba.persistence.entity.ActivityEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    public ResponseEntity<ActivityEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        ActivityEntity e = new ActivityEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(requiredString(body, "person_cpid"));
        e.setActivityType(requiredString(body, "activity_type"));
        if (body.get("title") instanceof String t) {
            e.setTitle(t);
        }
        e.setDurationMinutes(intOrNull(body.get("duration_minutes")));
        e.setCalories(intOrNull(body.get("calories")));
        e.setDistanceMeters(intOrNull(body.get("distance_meters")));
        e.setSteps(intOrNull(body.get("steps")));
        if (body.get("source") instanceof String s) {
            e.setSource(s);
        }
        e.setRecordedAt(parseTime(body.get("recorded_at")));
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.record(e));
    }

    @GetMapping
    public List<ActivityEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid,
            @RequestParam(value = "type", required = false) String type) {
        return activityService.list(tenantId, personCpid, type);
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(v.toString());
    }

    private static OffsetDateTime parseTime(Object v) {
        if (v == null) {
            return OffsetDateTime.now();
        }
        return OffsetDateTime.parse(v.toString());
    }
}
