package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tuso.core.FacilityGovernanceService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGovernanceCaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Facility governance journeys (FJ6/FJ7/FJ9). Opening a case (high-risk
 * update, transfer, duplicate report) is an operator/citizen action; DECIDING
 * is steward-only (in-service gate + FACILITY-UPDATE-HIGH-RISK / FACILITY-
 * TRANSFER / DUP-MERGE-STEWARD-ONLY specs).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/governance")
public class FacilityGovernanceController {

    private static final Set<String> STEWARD_ACTOR_TYPES =
            Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD");

    private final FacilityGovernanceService governanceService;

    public FacilityGovernanceController(FacilityGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @PostMapping("/high-risk-update")
    public ResponseEntity<Map<String, Object>> openHighRiskUpdate(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @RequestBody Map<String, Object> changes) {
        return created(governanceService.openHighRiskUpdate(facilityUuid, changes));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> openTransfer(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @RequestBody Map<String, Object> transfer) {
        return created(governanceService.openTransfer(facilityUuid, transfer));
    }

    @PostMapping("/duplicate-report")
    public ResponseEntity<Map<String, Object>> reportDuplicate(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @RequestBody(required = false) Map<String, Object> report) {
        return created(governanceService.reportDuplicate(facilityUuid, report));
    }

    @PostMapping("/cases/{caseId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @PathVariable("caseId") Long caseId,
            @RequestBody Map<String, String> body,
            HttpServletRequest http) {
        requireSteward(http);
        return ResponseEntity.ok(view(
                governanceService.decide(caseId, body.get("decision"), body.get("note"))));
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listOpen(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @RequestParam(value = "caseType", required = false) String caseType,
            HttpServletRequest http) {
        requireSteward(http);
        List<Map<String, Object>> cases = governanceService.listOpen(caseType)
                .stream().map(FacilityGovernanceController::view).toList();
        return ResponseEntity.ok(Map.of("cases", cases));
    }

    private ResponseEntity<Map<String, Object>> created(FacilityGovernanceCaseEntity c) {
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    private static void requireSteward(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        String normalised = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        if (!STEWARD_ACTOR_TYPES.contains(normalised)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Governance decisions are steward-only");
        }
    }

    private static Map<String, Object> view(FacilityGovernanceCaseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseRef", c.getCaseRef());
        m.put("caseType", c.getCaseType());
        m.put("facilityUuid", c.getFacilityUuid());
        m.put("status", c.getStatus());
        m.put("payload", c.getPayload());
        m.put("decidedBy", c.getDecidedBy());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
