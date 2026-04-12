package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.SleepService;
import zw.gov.mohcc.impilo.simba.persistence.entity.SleepSegmentEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/sleep")
public class SleepController {

    private final SleepService sleepService;

    public SleepController(SleepService sleepService) {
        this.sleepService = sleepService;
    }

    @PostMapping
    public ResponseEntity<SleepSegmentEntity> post(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        SleepSegmentEntity e = new SleepSegmentEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setStartTime(OffsetDateTime.parse(body.get("start_time").toString()));
        e.setEndTime(OffsetDateTime.parse(body.get("end_time").toString()));
        if (body.get("stage") instanceof String st) {
            e.setStage(st);
        }
        if (body.get("duration_minutes") != null) {
            e.setDurationMinutes(Integer.parseInt(body.get("duration_minutes").toString()));
        }
        if (body.get("source") instanceof String s) {
            e.setSource(s);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(sleepService.record(e));
    }

    @GetMapping
    public List<SleepSegmentEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return sleepService.list(tenantId, personCpid);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
