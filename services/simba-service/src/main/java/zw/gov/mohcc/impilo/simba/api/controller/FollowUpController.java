package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.FollowUpService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.persistence.entity.FollowUpActionEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness follow-up actions. Citizens read their own (read-only); providers/coaches create + update
 * (a provider actor is required to create). Person-scoped reads guarded citizen-self; provider-scoped
 * reads require a provider actor.
 */
@RestController
@RequestMapping("/internal/v1/wellness/follow-ups")
public class FollowUpController {

    private final FollowUpService followUpService;
    private final WellnessAccessGuard guard;

    public FollowUpController(FollowUpService followUpService, WellnessAccessGuard guard) {
        this.followUpService = followUpService;
        this.guard = guard;
    }

    private WellnessAccessGuard.WellnessAccessContext ctx(
            UUID tenantId, String actorId, String actorType, String providerId,
            String correlationId, String requestId) {
        return new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType, providerId,
                null, null, null, correlationId, requestId);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam(value = "person_cpid", required = false) String personCpid,
            @RequestParam(value = "provider_id", required = false) String queryProviderId) {
        if (queryProviderId != null) {
            // Provider view — must be a provider actor whose provider id matches the query.
            if (!"PROVIDER".equalsIgnoreCase(actorType) && !"SYSTEM".equalsIgnoreCase(actorType)) {
                throw new WellnessAccessGuard.WellnessAccessDeniedException("Provider actor required");
            }
            return Map.of("data", followUpService.forProvider(tenantId, queryProviderId));
        }
        if (personCpid == null || personCpid.isBlank()) {
            throw new IllegalArgumentException("Missing person_cpid or provider_id");
        }
        guard.requireSelf(ctx(tenantId, actorId, actorType, providerId, correlationId, requestId),
                personCpid, "wellness.follow-up.list");
        return Map.of("data", followUpService.forPerson(tenantId, personCpid));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestBody Map<String, Object> body) {
        // Creation is a provider/coach action (never citizen-self).
        boolean providerActor = ("PROVIDER".equalsIgnoreCase(actorType) || "SYSTEM".equalsIgnoreCase(actorType))
                && (providerId != null || "SYSTEM".equalsIgnoreCase(actorType));
        if (!providerActor) {
            throw new WellnessAccessGuard.WellnessAccessDeniedException(
                    "A provider actor is required to create a follow-up");
        }
        String personCpid = required(body, "person_cpid");
        LocalDate dueDate = body.get("due_date") == null ? null
                : LocalDate.parse(String.valueOf(body.get("due_date")));
        FollowUpActionEntity fu = followUpService.create(tenantId, personCpid, providerId,
                str(body, "origin_type"), str(body, "origin_ref"), str(body, "action_type"),
                required(body, "title"), str(body, "detail"), dueDate, str(body, "severity"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", fu));
    }

    @PatchMapping("/{followUpId}/status")
    public Map<String, Object> updateStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @PathVariable("followUpId") UUID followUpId,
            @RequestBody Map<String, Object> body) {
        boolean providerActor = ("PROVIDER".equalsIgnoreCase(actorType) || "SYSTEM".equalsIgnoreCase(actorType))
                && (providerId != null || "SYSTEM".equalsIgnoreCase(actorType));
        if (!providerActor) {
            throw new WellnessAccessGuard.WellnessAccessDeniedException(
                    "A provider actor is required to update a follow-up");
        }
        FollowUpActionEntity fu = followUpService.updateStatus(tenantId, followUpId, required(body, "status"));
        return Map.of("data", fu);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
