package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.CareLinkageService;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.persistence.entity.CareLinkageEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-to-care linkage (Simba records + routes; clinical owner keeps the workflow/results).
 * A wellness journey that surfaces risk records a linkage here; the BFF orchestrates the actual
 * PCT referral and stamps back the external reference.
 */
@RestController
@RequestMapping("/internal/v1/wellness/care-linkages")
public class CareLinkageController {

    private final CareLinkageService careLinkageService;
    private final WellnessAuditService audit;

    public CareLinkageController(CareLinkageService careLinkageService, WellnessAuditService audit) {
        this.careLinkageService = careLinkageService;
        this.audit = audit;
    }

    @GetMapping
    public List<CareLinkageEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return careLinkageService.list(tenantId, personCpid);
    }

    @PostMapping
    public ResponseEntity<CareLinkageEntity> route(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        CareLinkageEntity e = new CareLinkageEntity();
        e.setTenantId(tenantId);
        e.setPersonCpid(required(body, "person_cpid"));
        e.setTrigger(required(body, "trigger"));
        if (body.get("severity") instanceof String s && !s.isBlank()) e.setSeverity(s);
        if (body.get("reason") instanceof String r) e.setReason(r);
        if (body.get("target_owner") instanceof String o && !o.isBlank()) e.setTargetOwner(o);
        if (body.get("external_ref") instanceof String x && !x.isBlank()) e.setExternalRef(x);
        e.setCreatedBy(actorId);

        CareLinkageEntity saved = careLinkageService.route(e);
        audit.record(tenantId, actorId, actorType, null, saved.getPersonCpid(),
                WellnessAuditService.SELF, null, null, "wellness.care.route", "ALLOW",
                saved.getTrigger(), correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
