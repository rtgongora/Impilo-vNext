package zw.gov.mohcc.impilo.simba.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.CoachingService;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingRelationshipEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingTouchpointEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness coaching (Simba-owned relationship; coach identity stays Varapi). A coach may only
 * read/update participants they are actively assigned to — scope is enforced here and audited.
 * Coaching is NOT a clinical encounter; escalation routes to PCT via the care-linkage surface.
 */
@RestController
@RequestMapping("/internal/v1/wellness/coaching")
public class CoachingController {

    private final CoachingService coachingService;
    private final WellnessAuditService audit;

    public CoachingController(CoachingService coachingService, WellnessAuditService audit) {
        this.coachingService = coachingService;
        this.audit = audit;
    }

    @GetMapping("/relationships")
    public List<CoachingRelationshipEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(value = "person_cpid", required = false) String personCpid,
            @RequestParam(value = "coach_provider_id", required = false) String coachProviderId) {
        if (coachProviderId != null && !coachProviderId.isBlank()) {
            return coachingService.forCoach(tenantId, coachProviderId);
        }
        if (personCpid != null && !personCpid.isBlank()) {
            return coachingService.forPerson(tenantId, personCpid);
        }
        throw new IllegalArgumentException("person_cpid or coach_provider_id is required");
    }

    @PostMapping("/relationships")
    public ResponseEntity<CoachingRelationshipEntity> assign(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestBody Map<String, Object> body) {
        CoachingRelationshipEntity rel = new CoachingRelationshipEntity();
        rel.setTenantId(tenantId);
        rel.setPersonCpid(required(body, "person_cpid"));
        rel.setCoachProviderId(required(body, "coach_provider_id"));
        if (body.get("coach_cadre") instanceof String c) rel.setCoachCadre(c);
        if (body.get("focus") instanceof String f) rel.setFocus(f);
        if (body.get("plan_id") instanceof String p && !p.isBlank()) rel.setPlanId(UUID.fromString(p));
        rel.setRequestedBy(actorId != null ? actorId : rel.getPersonCpid());

        CoachingRelationshipEntity saved = coachingService.assign(rel);
        audit.record(tenantId, actorId, actorType, providerId, saved.getPersonCpid(),
                WellnessAuditService.COACH, null, null, "wellness.coaching.assign", "ALLOW", null,
                correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/relationships/{relationshipId}/touchpoints")
    public List<CoachingTouchpointEntity> touchpoints(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("relationshipId") UUID relationshipId) {
        coachingService.get(tenantId, relationshipId); // validate tenant
        return coachingService.touchpoints(relationshipId);
    }

    @PostMapping("/relationships/{relationshipId}/touchpoints")
    public ResponseEntity<CoachingTouchpointEntity> addTouchpoint(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @PathVariable("relationshipId") UUID relationshipId,
            @RequestBody Map<String, Object> body) {
        CoachingRelationshipEntity rel = coachingService.get(tenantId, relationshipId);

        // Scope-of-practice: a provider may only write to a participant they are assigned to.
        if (providerId != null && !providerId.isBlank()
                && !coachingService.coachIsAssigned(tenantId, providerId, relationshipId)) {
            audit.record(tenantId, actorId, actorType, providerId, rel.getPersonCpid(),
                    WellnessAuditService.COACH, null, null, "wellness.coaching.touchpoint.add",
                    "DENY", "COACH_NOT_ASSIGNED", correlationId, requestId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CoachingTouchpointEntity tp = new CoachingTouchpointEntity();
        if (body.get("touchpoint_type") instanceof String t && !t.isBlank()) tp.setTouchpointType(t);
        if (body.get("note") instanceof String n) tp.setNote(n);
        tp.setCreatedBy(actorId);
        CoachingTouchpointEntity saved = coachingService.addTouchpoint(tenantId, relationshipId, tp);

        audit.record(tenantId, actorId, actorType, providerId, rel.getPersonCpid(),
                WellnessAuditService.COACH, null, null, "wellness.coaching.touchpoint.add",
                "ALLOW", null, correlationId, requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }
}
