package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tuso.core.FacilityVerificationService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityVerificationCaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proof-of-place cases (FJ3/FJ4, D-L6). Opening a case is an operator action
 * (evidence in hand); DECIDING one is a verifier/steward action — gated here
 * in-service (defence-in-depth behind ext_authz + PLACE-VERIFY-REVIEW spec).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/verification-cases")
public class FacilityVerificationController {

    private static final Set<String> VERIFIER_ACTOR_TYPES =
            Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD", "INSPECTOR", "REGULATOR");

    private final FacilityVerificationService verificationService;

    public FacilityVerificationController(FacilityVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> open(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @RequestBody FacilityVerificationService.OpenCaseRequest request) {
        FacilityVerificationCaseEntity opened = verificationService.open(facilityUuid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(opened));
    }

    @PostMapping("/{caseId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable("facilityUuid") UUID facilityUuid,
            @PathVariable("caseId") Long caseId,
            @RequestBody Map<String, String> body,
            HttpServletRequest http) {
        requireVerifier(http);
        FacilityVerificationCaseEntity decided = verificationService.decide(
                caseId, body.get("decision"), body.get("note"));
        return ResponseEntity.ok(toView(decided));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable("facilityUuid") UUID facilityUuid, HttpServletRequest http) {
        requireVerifier(http);
        List<Map<String, Object>> cases = verificationService.listForFacility(facilityUuid)
                .stream().map(FacilityVerificationController::toView).toList();
        return ResponseEntity.ok(Map.of("cases", cases));
    }

    private static void requireVerifier(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        String normalised = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        if (!VERIFIER_ACTOR_TYPES.contains(normalised)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Verification review is verifier-only");
        }
    }

    private static Map<String, Object> toView(FacilityVerificationCaseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseRef", c.getCaseRef());
        m.put("facilityUuid", c.getFacilityUuid());
        m.put("verificationType", c.getVerificationType());
        m.put("status", c.getStatus());
        m.put("gpsDistanceMeters", c.getGpsDistanceMeters());
        m.put("evidenceRefs", c.getEvidenceRefs());
        m.put("rtcSessionRef", c.getRtcSessionRef());
        m.put("decidedBy", c.getDecidedBy());
        m.put("decidedAt", c.getDecidedAt());
        m.put("decisionNote", c.getDecisionNote());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}
