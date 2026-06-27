package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.service.GdhcnReadinessService;
import zw.gov.mohcc.impilo.tshepo.authz.service.GdhcnReadinessService.DomainReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GDHCN readiness cockpit API (Wave B B6). GET returns the full readiness picture for a tenant
 * (all domains, honest defaults); POST updates one domain and is gated in the service by an
 * admin role + a recent step-up.
 */
@RestController
@RequestMapping("/v1/gdhcn-readiness")
public class GdhcnReadinessController {

    private final GdhcnReadinessService service;

    public GdhcnReadinessController(GdhcnReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public List<DomainReadiness> list(@RequestHeader("x-tenant-id") UUID tenantId) {
        return service.listReadiness(tenantId);
    }

    @PostMapping("/{domainKey}")
    public DomainReadiness update(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader("x-actor-id") String actorId,
            @PathVariable String domainKey,
            @RequestBody UpdateReadinessRequest body) {
        return service.updateDomain(tenantId, actorId, realmRoles(jwt), domainKey,
                body.currentMaturity(), body.targetMaturity(), body.owner(),
                body.evidenceStatus(), body.remediationStatus(), body.linkedComponent());
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
        if (realm == null || !(realm.get("roles") instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(o.toString());
            }
        }
        return out;
    }

    public record UpdateReadinessRequest(
            TrustReadiness currentMaturity,
            TrustReadiness targetMaturity,
            String owner,
            String evidenceStatus,
            String remediationStatus,
            String linkedComponent) {}
}
