package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.ConsentPreferenceService;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.persistence.entity.ConsentPreferenceEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wellness consent preferences — read by WellnessAccessGuard. Citizen-self only. */
@RestController
@RequestMapping("/internal/v1/wellness/consent-preferences")
public class ConsentPreferenceController {

    private final ConsentPreferenceService consentService;
    private final WellnessAccessGuard guard;

    public ConsentPreferenceController(ConsentPreferenceService consentService, WellnessAccessGuard guard) {
        this.consentService = consentService;
        this.guard = guard;
    }

    private WellnessAccessGuard.WellnessAccessContext ctx(
            UUID tenantId, String actorId, String actorType, String correlationId, String requestId) {
        return new WellnessAccessGuard.WellnessAccessContext(tenantId, actorId, actorType, null,
                null, null, null, correlationId, requestId);
    }

    @GetMapping
    public Map<String, Object> get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestParam("person_cpid") String personCpid) {
        guard.requireSelf(ctx(tenantId, actorId, actorType, correlationId, requestId),
                personCpid, "wellness.consent.read");
        return Map.of("data", consentService.getOrDefault(tenantId, personCpid));
    }

    @PutMapping
    @SuppressWarnings("unchecked")
    public Map<String, Object> upsert(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        String personCpid = required(body, "person_cpid");
        guard.requireSelf(ctx(tenantId, actorId, actorType, correlationId, requestId),
                personCpid, "wellness.consent.update");
        List<String> categories = body.get("sensitive_categories") instanceof List<?> l
                ? (List<String>) l : null;
        ConsentPreferenceEntity saved = consentService.upsert(tenantId, personCpid,
                bool(body, "caregiver_involvement"), bool(body, "provider_involvement"),
                bool(body, "programme_involvement"), categories, str(body, "data_sharing_scope"),
                actorId != null ? actorId : personCpid);
        return Map.of("data", saved);
    }

    private static Boolean bool(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : Boolean.valueOf(String.valueOf(v));
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
