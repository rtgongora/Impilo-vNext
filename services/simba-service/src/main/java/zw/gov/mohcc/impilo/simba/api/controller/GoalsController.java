package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.GoalService;
import zw.gov.mohcc.impilo.simba.persistence.entity.GoalEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/goals")
public class GoalsController {

    private final GoalService goalService;

    public GoalsController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    public ResponseEntity<GoalEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        GoalEntity g = new GoalEntity();
        g.setTenantId(tenantId);
        g.setPersonCpid(required(body, "person_cpid"));
        g.setGoalType(required(body, "goal_type"));
        if (body.get("target_value") != null) {
            g.setTargetValue(new BigDecimal(body.get("target_value").toString()));
        }
        if (body.get("current_value") != null) {
            g.setCurrentValue(new BigDecimal(body.get("current_value").toString()));
        }
        if (body.get("unit") instanceof String u) {
            g.setUnit(u);
        }
        if (body.get("period") instanceof String p) {
            g.setPeriod(p);
        }
        if (body.get("status") instanceof String s) {
            g.setStatus(s);
        }
        if (body.get("target_date") != null) {
            g.setTargetDate(LocalDate.parse(body.get("target_date").toString()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(g));
    }

    @GetMapping
    public List<GoalEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return goalService.list(tenantId, personCpid);
    }

    @PutMapping("/{id}/progress")
    public GoalEntity progress(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        if (body.get("current_value") == null) {
            throw new IllegalArgumentException("Missing current_value");
        }
        BigDecimal cv = new BigDecimal(body.get("current_value").toString());
        return goalService.updateProgress(id, tenantId, cv);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
