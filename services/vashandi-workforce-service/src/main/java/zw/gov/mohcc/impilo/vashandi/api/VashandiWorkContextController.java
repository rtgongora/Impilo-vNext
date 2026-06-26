package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceContextService;

import java.util.UUID;

/**
 * C2 Workforce work-context read-model endpoint.
 *
 * {@code GET /v1/internal/vashandi/work-context?actorId={healthId}} returns the
 * active assignments + check-in state + affiliations + requiresContextChooser for
 * an actor, so the shell can render the WHERE/WHAT context picker and the Cadre
 * Engine can derive clinical scope.
 *
 * Internal route — reached only behind the Envoy ext_authz → TSHEPO gate
 * (CONTEXT-SELECT / WORK-REQUIRES-ASSIGNMENT specced to track P). Anti-enumeration:
 * an unknown actor returns an empty (unresolved) context, never a 404.
 */
@RestController
@RequestMapping("/v1/internal/vashandi/work-context")
public class VashandiWorkContextController {

    private final WorkforceContextService workforceContextService;

    public VashandiWorkContextController(WorkforceContextService workforceContextService) {
        this.workforceContextService = workforceContextService;
    }

    @GetMapping
    public VashandiDtos.WorkforceContextResponse workContext(
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "workforceProfileId", required = false) UUID workforceProfileId) {
        UUID tenantId = tenantId();
        if (workforceProfileId != null) {
            return workforceContextService.resolveByProfileId(tenantId, workforceProfileId);
        }
        String healthId = (actorId != null && !actorId.isBlank()) ? actorId : actorIdFromContext();
        return workforceContextService.resolveByHealthId(tenantId, healthId);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actorIdFromContext() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.actorId();
    }
}
