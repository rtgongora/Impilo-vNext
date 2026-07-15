package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.TimelineService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Person-facing wellness timeline (keyset-paginated). Citizen-self, guarded. */
@RestController
@RequestMapping("/internal/v1/wellness/timeline")
public class TimelineController {

    private final TimelineService timelineService;
    private final WellnessAccessGuard guard;

    public TimelineController(TimelineService timelineService, WellnessAccessGuard guard) {
        this.timelineService = timelineService;
        this.guard = guard;
    }

    @GetMapping
    public Map<String, Object> timeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam("person_cpid") String personCpid,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        guard.requireSelf(new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType,
                        providerId, null, null, null, correlationId, requestId),
                personCpid, "wellness.timeline.read");
        TimelineService.TimelinePage page = timelineService.page(tenantId, personCpid, cursor, limit);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("next_cursor", page.nextCursor());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", page.events());
        out.put("meta", meta);
        return out;
    }
}
