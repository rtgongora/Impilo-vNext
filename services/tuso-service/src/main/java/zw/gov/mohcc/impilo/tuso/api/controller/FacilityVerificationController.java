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
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.FacilityVerificationService;
import zw.gov.mohcc.impilo.tuso.core.MinistryAuthorityService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityVerificationCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Proof-of-place cases (FJ3/FJ4, D-L6). Opening a case is an operator action
 * (evidence in hand); DECIDING one is a verifier/steward action — gated here
 * in-service (defence-in-depth behind ext_authz + PLACE-VERIFY-REVIEW spec).
 */
@RestController
@RequestMapping("/v1/internal/facilities/{facilityUuid}/verification-cases")
public class FacilityVerificationController {

    /**
     * Service-to-service callers that carry no person and therefore no appointment. Kept narrow and
     * explicit: a SYSTEM actor is the platform acting on its own behalf, not a person asserting a
     * role. Every human path resolves through {@link MinistryAuthorityService}.
     */
    /**
     * The platform-internal bypass, not a permission set. Deliberately kept at exactly
     * {@code {SYSTEM}} rather than widened to {@link ActorTypeGuard#MACHINE_ONLY}: this is the
     * condition under which the Ministry-appointment check below is SKIPPED, so adding
     * {@code SERVICE} to it would weaken the strongest guard in this controller, not strengthen it.
     * Expressed as a Duty so the emittable-vocabulary invariant applies here too.
     */
    private static final ActorTypeGuard.Duty PLATFORM_INTERNAL_CALL =
            ActorTypeGuard.Duty.of("a platform-internal verification call", Set.of("SYSTEM"));

    private final FacilityVerificationService verificationService;
    private final MinistryAuthorityService ministryAuthority;
    private final FacilityRepository facilityRepository;

    public FacilityVerificationController(FacilityVerificationService verificationService,
                                          MinistryAuthorityService ministryAuthority,
                                          FacilityRepository facilityRepository) {
        this.verificationService = verificationService;
        this.ministryAuthority = ministryAuthority;
        this.facilityRepository = facilityRepository;
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
        requireVerifier(facilityUuid, http);
        FacilityVerificationCaseEntity decided = verificationService.decide(
                caseId, body.get("decision"), body.get("note"));
        return ResponseEntity.ok(toView(decided));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable("facilityUuid") UUID facilityUuid, HttpServletRequest http) {
        requireVerifier(facilityUuid, http);
        List<Map<String, Object>> cases = verificationService.listForFacility(facilityUuid)
                .stream().map(FacilityVerificationController::toView).toList();
        return ResponseEntity.ok(Map.of("cases", cases));
    }

    /**
     * Verifying authority over <em>this</em> facility (FCV-W2).
     *
     * <p>This previously read {@code X-Actor-Type} straight off the request and accepted any of six
     * role-ish strings. That header is supplied by the caller, and the policy rule covering this
     * path is an unconditional ALLOW, so the decision was effectively ungated — asserting
     * {@code X-Actor-Type: REGISTRY_ADMIN} was enough. Authority now comes from an ACTIVE Ministry
     * appointment in org-registry whose jurisdiction covers this facility's province or district,
     * so a district officer cannot decide outside their district and no header can grant anything.
     * If org-registry is unreachable the lookup returns nothing and the act is refused.</p>
     */
    private void requireVerifier(UUID facilityUuid, HttpServletRequest http) {
        TrustContext ctx = TrustContextHolder.require();
        String actorType = http.getHeader("X-Actor-Type");
        if (ActorTypeGuard.permits(actorType, PLATFORM_INTERNAL_CALL) && ctx.actorId() == null) {
            return; // platform-internal call, carries no person to appoint
        }
        FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid)
                .filter(f -> f.getTenantId() == null || f.getTenantId().equals(ctx.tenantId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Facility not found: " + facilityUuid));

        if (ministryAuthority.authorityOver(ctx.actorId(), facility,
                MinistryAuthorityService.VERIFIER_ROLES).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Verification review requires an active Ministry verifier appointment whose "
                            + "jurisdiction covers this facility.");
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
