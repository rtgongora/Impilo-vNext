package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.HaemovigilanceService;
import zw.gov.mohcc.impilo.madi.persistence.entity.AdverseTransfusionReactionEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.HaemovigilanceCaseEntity;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/haemovigilance")
public class MadiHaemovigilanceController {

    private final HaemovigilanceService haemovigilanceService;

    public MadiHaemovigilanceController(HaemovigilanceService haemovigilanceService) {
        this.haemovigilanceService = haemovigilanceService;
    }

    @PostMapping("/reactions")
    public ResponseEntity<AdverseTransfusionReactionEntity> reportReaction(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(haemovigilanceService.reportReaction(
                tenantId, UUID.fromString(required(body, "episode_id")),
                required(body, "reaction_type"), required(body, "severity"),
                str(body, "description"), str(body, "reported_by"), facilityId));
    }

    @PostMapping("/cases")
    public ResponseEntity<HaemovigilanceCaseEntity> openCase(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(haemovigilanceService.openCase(
                tenantId, UUID.fromString(required(body, "reaction_id")),
                str(body, "assigned_to"), facilityId));
    }

    @PostMapping("/cases/{caseId}/investigate")
    public HaemovigilanceCaseEntity investigate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID caseId,
            @RequestBody Map<String, Object> body) {
        return haemovigilanceService.investigate(tenantId, caseId,
                str(body, "notes"), str(body, "assigned_to"));
    }

    @PostMapping("/cases/{caseId}/close")
    public HaemovigilanceCaseEntity close(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID caseId,
            @RequestBody Map<String, Object> body) {
        return haemovigilanceService.closeCase(tenantId, caseId, str(body, "notes"));
    }

    @GetMapping("/national-dashboard")
    public Map<String, Object> nationalDashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return haemovigilanceService.nationalDashboard(tenantId);
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
        return v != null ? v.toString() : null;
    }
}
