package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Local wellness access guard. Simba carries NO rego — central policy stays in OPA + tshepo.
 * This guard enforces the wellness-specific access shapes from the request's trust context plus the
 * subject's Simba consent preferences, and audits every decision (ALLOW/DENY) via
 * {@link WellnessAuditService}. Controllers build a {@link WellnessAccessContext} from CompanionHeaders
 * and call the matching check at the top of each handler.
 *
 * <p>The seven shapes:
 * <ol>
 *   <li>citizen-self — actor == subject</li>
 *   <li>provider-client — provider with an active coaching assignment AND consent provider_involvement</li>
 *   <li>facility-scoped — provider/operator acting within a facility context</li>
 *   <li>programme-scoped — programme officer/admin with consent programme_involvement</li>
 *   <li>caregiver-dependent — caregiver with consent caregiver_involvement</li>
 *   <li>above-site aggregate — no client-level rows (enforced by the aggregate service producing no cpid)</li>
 *   <li>sensitive-data filtering — mood/mental/reproductive gated on consented sensitive_categories</li>
 * </ol>
 */
@Service
public class WellnessAccessGuard {

    private final CoachingService coachingService;
    private final ConsentPreferenceService consentService;
    private final WellnessAuditService audit;

    public WellnessAccessGuard(CoachingService coachingService,
                               ConsentPreferenceService consentService,
                               WellnessAuditService audit) {
        this.coachingService = coachingService;
        this.consentService = consentService;
        this.audit = audit;
    }

    /** Immutable per-request access context derived from CompanionHeaders. */
    public record WellnessAccessContext(
            UUID tenantId,
            String actorId,
            String actorType,   // CITIZEN | PROVIDER | CAREGIVER | OPERATOR | SYSTEM | PROGRAMME_ADMIN
            String providerId,
            String facilityId,
            String workspaceId,
            String programmeId,
            String correlationId,
            String requestId) {
    }

    /** Thrown when a wellness access check fails; controllers/handler map this to HTTP 403. */
    public static class WellnessAccessDeniedException extends RuntimeException {
        public WellnessAccessDeniedException(String message) {
            super(message);
        }
    }

    private boolean isSelf(WellnessAccessContext ctx, String subjectCpid) {
        return ctx.actorId() != null && ctx.actorId().equals(subjectCpid);
    }

    private boolean isSystem(WellnessAccessContext ctx) {
        return "SYSTEM".equalsIgnoreCase(ctx.actorType());
    }

    // ── citizen-self ─────────────────────────────────────────────────────────────────
    /** Person acting on their own wellness data. Denies cross-person here (use the provider path). */
    public void requireSelf(WellnessAccessContext ctx, String subjectCpid, String action) {
        if (isSelf(ctx, subjectCpid) || isSystem(ctx)) {
            allow(ctx, subjectCpid, WellnessAuditService.SELF, action);
            return;
        }
        deny(ctx, subjectCpid, WellnessAuditService.SELF, action, "not the subject");
    }

    // ── provider-client ──────────────────────────────────────────────────────────────
    /**
     * Provider reading/writing a client's wellness data. Requires (a) an active coaching relationship
     * for {@code relationshipId} owned by this provider AND (b) consent provider_involvement.
     */
    public void requireProviderClient(WellnessAccessContext ctx, String subjectCpid,
                                      UUID relationshipId, String action) {
        if (isSystem(ctx)) {
            allow(ctx, subjectCpid, WellnessAuditService.COACH, action);
            return;
        }
        boolean assigned = ctx.providerId() != null && relationshipId != null
                && coachingService.coachIsAssigned(ctx.tenantId(), ctx.providerId(), relationshipId);
        if (!assigned) {
            deny(ctx, subjectCpid, WellnessAuditService.COACH, action, "no active coaching assignment");
            return;
        }
        if (!consentService.getOrDefault(ctx.tenantId(), subjectCpid).isProviderInvolvement()) {
            deny(ctx, subjectCpid, WellnessAuditService.COACH, action, "provider involvement not consented");
            return;
        }
        allow(ctx, subjectCpid, WellnessAuditService.COACH, action);
    }

    // ── programme-scoped ─────────────────────────────────────────────────────────────
    /** Programme officer/admin acting within their programme, needs consent programme_involvement. */
    public void requireProgramme(WellnessAccessContext ctx, String subjectCpid, String action) {
        if (isSystem(ctx)) {
            allow(ctx, subjectCpid, WellnessAuditService.PROGRAMME_ADMIN, action);
            return;
        }
        boolean programmeActor = ctx.programmeId() != null
                && ("PROGRAMME_ADMIN".equalsIgnoreCase(ctx.actorType())
                    || "OPERATOR".equalsIgnoreCase(ctx.actorType()));
        if (!programmeActor) {
            deny(ctx, subjectCpid, WellnessAuditService.PROGRAMME_ADMIN, action, "not a programme actor");
            return;
        }
        if (!consentService.getOrDefault(ctx.tenantId(), subjectCpid).isProgrammeInvolvement()) {
            deny(ctx, subjectCpid, WellnessAuditService.PROGRAMME_ADMIN, action, "programme involvement not consented");
            return;
        }
        allow(ctx, subjectCpid, WellnessAuditService.PROGRAMME_ADMIN, action);
    }

    // ── caregiver-dependent ──────────────────────────────────────────────────────────
    /** Caregiver acting for a dependant, needs consent caregiver_involvement. */
    public void requireCaregiver(WellnessAccessContext ctx, String subjectCpid, String action) {
        if (isSystem(ctx)) {
            allow(ctx, subjectCpid, WellnessAuditService.DEPENDANT, action);
            return;
        }
        boolean caregiverActor = "CAREGIVER".equalsIgnoreCase(ctx.actorType());
        if (!caregiverActor) {
            deny(ctx, subjectCpid, WellnessAuditService.DEPENDANT, action, "not a caregiver actor");
            return;
        }
        if (!consentService.getOrDefault(ctx.tenantId(), subjectCpid).isCaregiverInvolvement()) {
            deny(ctx, subjectCpid, WellnessAuditService.DEPENDANT, action, "caregiver involvement not consented");
            return;
        }
        allow(ctx, subjectCpid, WellnessAuditService.DEPENDANT, action);
    }

    // ── above-site aggregate ─────────────────────────────────────────────────────────
    /** Aggregate/dashboard access — provider/operator/programme-admin, never citizen; no cpid subject. */
    public void requireAggregate(WellnessAccessContext ctx, String action) {
        String t = ctx.actorType() == null ? "" : ctx.actorType().toUpperCase();
        boolean allowed = t.equals("PROVIDER") || t.equals("OPERATOR")
                || t.equals("PROGRAMME_ADMIN") || t.equals("SYSTEM");
        if (allowed) {
            audit.record(ctx.tenantId(), ctx.actorId(), ctx.actorType(), ctx.providerId(), null,
                    WellnessAuditService.PROGRAMME_ADMIN, ctx.facilityId(), ctx.workspaceId(),
                    action, "ALLOW", null, ctx.correlationId(), ctx.requestId());
            return;
        }
        audit.record(ctx.tenantId(), ctx.actorId(), ctx.actorType(), ctx.providerId(), null,
                WellnessAuditService.PROGRAMME_ADMIN, ctx.facilityId(), ctx.workspaceId(),
                action, "DENY", "not an aggregate-eligible actor", ctx.correlationId(), ctx.requestId());
        throw new WellnessAccessDeniedException("Aggregate access denied");
    }

    // ── sensitive-category filtering ─────────────────────────────────────────────────
    /**
     * True when the subject has consented to sharing the given sensitive category with a non-self
     * viewer. Self and system always see their own sensitive data.
     */
    public boolean sensitiveCategoryVisible(WellnessAccessContext ctx, String subjectCpid, String category) {
        if (isSelf(ctx, subjectCpid) || isSystem(ctx)) {
            return true;
        }
        return consentService.consentedSensitiveCategories(ctx.tenantId(), subjectCpid)
                .contains(category == null ? "" : category.toUpperCase());
    }

    private void allow(WellnessAccessContext ctx, String subjectCpid, String relationship, String action) {
        audit.record(ctx.tenantId(), ctx.actorId(), ctx.actorType(), ctx.providerId(), subjectCpid,
                relationship, ctx.facilityId(), ctx.workspaceId(), action, "ALLOW", null,
                ctx.correlationId(), ctx.requestId());
    }

    private void deny(WellnessAccessContext ctx, String subjectCpid, String relationship,
                      String action, String reason) {
        audit.record(ctx.tenantId(), ctx.actorId(), ctx.actorType(), ctx.providerId(), subjectCpid,
                relationship, ctx.facilityId(), ctx.workspaceId(), action, "DENY", reason,
                ctx.correlationId(), ctx.requestId());
        throw new WellnessAccessDeniedException("Wellness access denied: " + reason);
    }
}
