package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.HabitService;
import zw.gov.mohcc.impilo.simba.persistence.entity.HabitCheckInEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lightweight daily habit check-ins (Simba-owned wellness depth). */
@RestController
@RequestMapping("/internal/v1/wellness/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping("/check-ins")
    public List<HabitCheckInEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return habitService.list(tenantId, personCpid);
    }

    @PostMapping("/check-ins")
    public ResponseEntity<HabitCheckInEntity> checkIn(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        HabitCheckInEntity e = new HabitCheckInEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setHabitCode(required(body, "habit_code"));
        if (body.get("status") instanceof String s && !s.isBlank()) {
            e.setStatus(s);
        }
        if (body.get("note") instanceof String n) {
            e.setNote(n);
        }
        if (body.get("goal_id") instanceof String g && !g.isBlank()) {
            e.setGoalId(UUID.fromString(g));
        }
        if (body.get("enrollment_id") instanceof String en && !en.isBlank()) {
            e.setEnrollmentId(UUID.fromString(en));
        }
        if (body.get("check_in_date") instanceof String d && !d.isBlank()) {
            e.setCheckInDate(LocalDate.parse(d));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(habitService.checkIn(e));
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
