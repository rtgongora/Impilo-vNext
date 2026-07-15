package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.social.core.ProgrammeEngagementService;

import java.util.Map;
import java.util.UUID;

/**
 * Programme-engagement dashboard (sovereign, /internal/v1/wellness/social/programme-engagement).
 * Aggregate-only — gated to PROGRAMME_ADMIN via {@link WellnessAccessGuard#requireAggregate}. Returns
 * counts/rates only; no per-person social content ever leaves this endpoint.
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/programme-engagement")
public class ProgrammeEngagementController {

    private final ProgrammeEngagementService engagement;
    private final WellnessAccessGuard guard;

    public ProgrammeEngagementController(ProgrammeEngagementService engagement,
                                         WellnessAccessGuard guard) {
        this.engagement = engagement;
        this.guard = guard;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.WORKSPACE_ID, required = false) String workspaceId,
            @RequestHeader(value = CompanionHeaders.PROGRAMME_ID, required = false) String programmeHeader,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam(value = "programme_id", required = false) UUID programmeParam) {
        UUID programmeId = programmeParam != null ? programmeParam
                : (programmeHeader != null && !programmeHeader.isBlank()
                    ? UUID.fromString(programmeHeader) : null);
        WellnessAccessGuard.WellnessAccessContext ctx = new WellnessAccessGuard.WellnessAccessContext(
                tenantId, actorId, actorType, providerId, facilityId, workspaceId,
                programmeId == null ? null : programmeId.toString(), correlationId, requestId);
        guard.requireAggregate(ctx, "wellness.social.programme-engagement.dashboard");
        return Map.of("data", engagement.dashboard(tenantId, programmeId));
    }
}
