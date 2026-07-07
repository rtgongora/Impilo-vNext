package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.core.WellnessAggregateService;

import java.util.Map;
import java.util.UUID;

/** Above-site wellness aggregate dashboard. Provider/operator/programme-admin only; never leaks cpid. */
@RestController
@RequestMapping("/internal/v1/wellness/aggregate")
public class WellnessAggregateController {

    private final WellnessAggregateService aggregateService;
    private final WellnessAccessGuard guard;

    public WellnessAggregateController(WellnessAggregateService aggregateService, WellnessAccessGuard guard) {
        this.aggregateService = aggregateService;
        this.guard = guard;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.PROGRAMME_ID, required = false) String programmeId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId) {
        guard.requireAggregate(new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType,
                providerId, facilityId, null, programmeId, correlationId, requestId),
                "wellness.aggregate.dashboard");
        return Map.of("data", aggregateService.dashboard(tenantId));
    }
}
