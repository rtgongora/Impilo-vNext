package zw.gov.mohcc.impilo.indawo.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.indawo.core.SiteSelfServiceService;
import zw.gov.mohcc.impilo.indawo.domain.SiteOperatorGrantEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteRegistrationCaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SJ1/SJ2 self-service lanes (D-L4/D-L5). Submissions are operator/citizen
 * actions with generic responses; approvals and steward queues are gated
 * in-service (defence-in-depth behind ext_authz + the SITE-OPERATOR-ROLE /
 * SITE-REGISTER policy specs).
 */
@RestController
public class SiteSelfServiceController {

    private static final Set<String> STEWARD_ACTOR_TYPES =
            Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD", "INSPECTOR", "REGULATOR");

    private final SiteSelfServiceService selfService;

    public SiteSelfServiceController(SiteSelfServiceService selfService) {
        this.selfService = selfService;
    }

    // ── SJ1: operator grants ──────────────────────────────────────────────────

    @PostMapping("/internal/v1/sites/{siteId}/operator-grants")
    public ResponseEntity<Map<String, Object>> submitGrant(
            @PathVariable("siteId") UUID siteId,
            @RequestBody SiteSelfServiceService.SubmitGrantClaim request) {
        SiteOperatorGrantEntity grant = selfService.submitGrantClaim(siteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(grantView(grant));
    }

    @PostMapping("/internal/v1/site-operator-grants/{grantId}/approve")
    public ResponseEntity<Map<String, Object>> approveGrant(
            @PathVariable("grantId") Long grantId, HttpServletRequest http) {
        requireSteward(http);
        SiteOperatorGrantEntity grant = selfService.approveGrant(grantId, http.getHeader("X-Actor-ID"));
        return ResponseEntity.ok(grantView(grant));
    }

    @GetMapping("/internal/v1/site-operator-grants")
    public ResponseEntity<Map<String, Object>> grantQueue(
            @RequestParam("state") String state, HttpServletRequest http) {
        requireSteward(http);
        List<Map<String, Object>> grants = selfService.listGrantsByState(state)
                .stream().map(SiteSelfServiceController::grantView).toList();
        return ResponseEntity.ok(Map.of("grants", grants));
    }

    // ── SJ2: registration ─────────────────────────────────────────────────────

    @PostMapping("/internal/v1/site-registrations")
    public ResponseEntity<SiteSelfServiceService.RegistrationReceipt> register(
            @RequestBody SiteSelfServiceService.RegisterSiteRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(selfService.registerSite(request));
    }

    /** Steward review queue — carries steward-only match context. */
    @GetMapping("/internal/v1/site-registrations")
    public ResponseEntity<Map<String, Object>> caseQueue(
            @RequestParam("outcome") String outcome, HttpServletRequest http) {
        requireSteward(http);
        List<Map<String, Object>> cases = selfService.listCasesByOutcome(outcome)
                .stream().map(SiteSelfServiceController::caseView).toList();
        return ResponseEntity.ok(Map.of("cases", cases));
    }

    private static void requireSteward(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        String normalised = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        if (!STEWARD_ACTOR_TYPES.contains(normalised)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Steward-reviewed surface");
        }
    }

    private static Map<String, Object> grantView(SiteOperatorGrantEntity g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("siteId", g.getSiteId());
        m.put("personHealthId", g.getPersonHealthId());
        m.put("role", g.getRole());
        m.put("approvalState", g.getApprovalState());
        m.put("claimType", g.getClaimType());
        m.put("validFrom", g.getValidFrom());
        m.put("validTo", g.getValidTo());
        return m;
    }

    private static Map<String, Object> caseView(SiteRegistrationCaseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("caseRef", c.getCaseRef());
        m.put("submittedName", c.getSubmittedName());
        m.put("siteCategory", c.getSiteCategory());
        m.put("applicantHealthId", c.getApplicantHealthId());
        m.put("outcome", c.getOutcome());
        m.put("matchedSiteId", c.getMatchedSiteId());
        m.put("matchContext", c.getMatchContext());
        m.put("provisionalSiteId", c.getProvisionalSiteId());
        m.put("evidenceRef", c.getEvidenceRef());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
