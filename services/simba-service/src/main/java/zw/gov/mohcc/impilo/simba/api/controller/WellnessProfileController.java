package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessProfileEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.WellnessProfileRepository;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/wellness/profiles")
public class WellnessProfileController {

    private final WellnessProfileRepository wellnessProfileRepository;

    public WellnessProfileController(WellnessProfileRepository wellnessProfileRepository) {
        this.wellnessProfileRepository = wellnessProfileRepository;
    }

    @GetMapping("/{cpid}")
    public ResponseEntity<WellnessProfileEntity> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("cpid") String cpid) {
        return wellnessProfileRepository.findByTenantIdAndPersonCpid(tenantId, cpid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{cpid}")
    public ResponseEntity<WellnessProfileEntity> put(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("cpid") String cpid,
            @RequestBody Map<String, Object> body) {
        WellnessProfileEntity entity = wellnessProfileRepository
                .findByTenantIdAndPersonCpid(tenantId, cpid)
                .orElseGet(WellnessProfileEntity::new);
        entity.setTenantId(tenantId);
        entity.setPersonCpid(cpid);
        if (body.get("display_name") instanceof String s) {
            entity.setDisplayName(s);
        }
        if (body.get("goals_json") instanceof String gj) {
            entity.setGoalsJson(gj);
        } else if (body.get("goalsJson") instanceof String gj2) {
            entity.setGoalsJson(gj2);
        }
        if (body.get("preferences_json") instanceof String pj) {
            entity.setPreferencesJson(pj);
        } else if (body.get("preferencesJson") instanceof String pj2) {
            entity.setPreferencesJson(pj2);
        }
        return ResponseEntity.ok(wellnessProfileRepository.save(entity));
    }
}
