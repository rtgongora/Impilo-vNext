package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.IntegrationEventService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;

import java.util.Map;
import java.util.UUID;

/** Read-only handoff ledger for a person (honest PENDING/UNSUPPORTED seam visibility). Citizen-self. */
@RestController
@RequestMapping("/internal/v1/wellness/integration-events")
public class IntegrationEventController {

    private final IntegrationEventService integrations;
    private final WellnessAccessGuard guard;

    public IntegrationEventController(IntegrationEventService integrations, WellnessAccessGuard guard) {
        this.integrations = integrations;
        this.guard = guard;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam("person_cpid") String personCpid) {
        guard.requireSelf(new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType,
                        null, null, null, null, correlationId, requestId),
                personCpid, "wellness.integration-events.read");
        return Map.of("data", integrations.forPerson(tenantId, personCpid));
    }
}
