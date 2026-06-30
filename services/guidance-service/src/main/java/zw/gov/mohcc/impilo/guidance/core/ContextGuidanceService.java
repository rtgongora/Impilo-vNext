package zw.gov.mohcc.impilo.guidance.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceDismissalEntity;
import zw.gov.mohcc.impilo.guidance.persistence.entity.GuidanceItemEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GuidanceDismissalRepository;
import zw.gov.mohcc.impilo.guidance.persistence.repository.GuidanceItemRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nompilo context-aware guidance engine — the on-platform guide.
 *
 * <p>Resolves a caller's context (route, user-type, active role, trust level, relationship) plus a
 * set of already-evaluated <em>signals</em> (e.g. {@code trust.not_verified}, {@code payments.outstanding},
 * {@code provider.not_checked_in}) against the config-driven {@code guidance.guidance_item} catalogue,
 * then ranks the applicable items, removes ones the person has dismissed, and returns a structured
 * guidance set. Each item routes to a sovereign owner — Nompilo guides, it never owns the transaction.</p>
 *
 * <p>The signals are computed by the BFF from the real service state it already aggregates (trust,
 * profile completion, outstanding bills, provider check-in, facility setup). This keeps the engine
 * config-driven and free of fake intelligence: nothing is shown unless a real signal fired or the
 * item is unconditional ({@code always}).</p>
 */
@Service
public class ContextGuidanceService {

    private static final Logger log = LoggerFactory.getLogger(ContextGuidanceService.class);

    private final GuidanceItemRepository itemRepo;
    private final GuidanceDismissalRepository dismissalRepo;

    public ContextGuidanceService(GuidanceItemRepository itemRepo, GuidanceDismissalRepository dismissalRepo) {
        this.itemRepo = itemRepo;
        this.dismissalRepo = dismissalRepo;
    }

    /** The resolved context the BFF passes in (already trust/policy-resolved upstream). */
    public record GuidanceContext(
            String tenantId,
            String actorId,
            String userType,       // CITIZEN | PROVIDER | GUARDIAN | FACILITY_ADMIN | SUPPORT
            String activeRole,     // optional
            int trustLoa,          // 1..4
            String routePath,      // current route
            String relationship,   // SELF | GUARDIAN | CAREGIVER | PROXY | FACILITY_ASSISTED
            Set<String> signals    // evaluated condition keys that fired (e.g. "trust.not_verified")
    ) {
        public GuidanceContext {
            userType = userType == null ? "CITIZEN" : userType.toUpperCase();
            relationship = relationship == null ? "SELF" : relationship.toUpperCase();
            routePath = routePath == null ? "" : routePath;
            signals = signals == null ? Set.of() : signals;
        }
    }

    /**
     * Resolve the contextual guidance set for a caller. Returns a ranked, dismissal-filtered list of
     * applicable items. Items requiring an audit are flagged so the caller (BFF) emits an audit event.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> resolve(GuidanceContext ctx) {
        List<GuidanceItemEntity> all = itemRepo.findByTenantIdAndStatusOrderByPriorityAsc(ctx.tenantId(), "ACTIVE");
        if (all.isEmpty()) {
            // No catalogue for this tenant yet: also evaluate the national-spine default set so a tenant
            // without its own overrides still gets the canonical guidance.
            all = itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("national-spine", "ACTIVE");
        }
        Set<String> dismissed = dismissalRepo.findByTenantIdAndActorId(ctx.tenantId(), ctx.actorId())
                .stream().map(GuidanceDismissalEntity::getGuidanceKey).collect(Collectors.toSet());

        return all.stream()
                .filter(item -> applies(item, ctx))
                .filter(item -> !dismissed.contains(item.getGuidanceKey()))
                .sorted(Comparator.comparingInt(GuidanceItemEntity::getPriority))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /** Map a denied/locked reason code to a safe plain-language explanation (no sensitive leak). */
    @Transactional(readOnly = true)
    public Map<String, Object> explainLocked(String tenantId, String reasonCode, GuidanceContext ctx) {
        // First try a catalogue LOCKED_STATE item whose trigger matches the reason; else a safe default.
        String normalized = reasonCode == null ? "" : reasonCode.trim().toUpperCase();
        List<GuidanceItemEntity> items = itemRepo.findByTenantIdAndStatusOrderByPriorityAsc(tenantId, "ACTIVE");
        if (items.isEmpty()) {
            items = itemRepo.findByTenantIdAndStatusOrderByPriorityAsc("national-spine", "ACTIVE");
        }
        Optional<GuidanceItemEntity> match = items.stream()
                .filter(i -> "LOCKED_STATE".equals(i.getGuidanceType()))
                .filter(i -> matchesReason(i, normalized))
                .findFirst();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reasonCode", normalized);
        if (match.isPresent()) {
            GuidanceItemEntity i = match.get();
            out.put("title", i.getTitle());
            out.put("explanation", i.getBody());
            out.put("ctaLabel", i.getCtaLabel());
            out.put("ctaRoute", i.getCtaRoute());
            out.put("ownerService", i.getOwnerService());
            out.put("khulumaFollowUpAvailable", i.isKhulumaFollowUp());
            out.put("auditRequired", i.isAuditRequired());
            out.put("guidanceKey", i.getGuidanceKey());
        } else {
            out.put("title", "This action isn't available right now");
            out.put("explanation", safeDefaultFor(normalized));
            out.put("ctaLabel", "Get help");
            out.put("ctaRoute", "/guidance");
            out.put("ownerService", "guidance");
            out.put("khulumaFollowUpAvailable", false);
            // Denied explanations involving sensitive resources are always audited, even on the default path.
            out.put("auditRequired", isSensitiveReason(normalized));
        }
        // We NEVER echo the hidden data — only the reason class and the safe next step.
        out.put("safetyBoundary",
                "Nompilo explains why this is restricted and what you can do next. It does not reveal "
                        + "the protected information itself.");
        return out;
    }

    // ── applicability ────────────────────────────────────────────────────────────────

    private boolean applies(GuidanceItemEntity item, GuidanceContext ctx) {
        // user-type gate
        if (notWildcard(item.getAppliesUserType()) && !item.getAppliesUserType().equalsIgnoreCase(ctx.userType())) {
            return false;
        }
        // role gate
        if (notWildcard(item.getAppliesRole())
                && (ctx.activeRole() == null || !item.getAppliesRole().equalsIgnoreCase(ctx.activeRole()))) {
            return false;
        }
        // route gate (prefix match; blank route = global)
        if (notWildcard(item.getAppliesRoute()) && !ctx.routePath().startsWith(item.getAppliesRoute())) {
            return false;
        }
        // trust gate: only show if the caller meets the minimum LoA this guidance is relevant at.
        // (A LOCKED_STATE item is the exception — it is explicitly the "you don't yet meet trust" card,
        //  so it should still show when the trigger condition for being under-trust fired.)
        if (item.getMinTrustLoa() != null && ctx.trustLoa() < item.getMinTrustLoa()
                && !"LOCKED_STATE".equals(item.getGuidanceType())) {
            return false;
        }
        // trigger condition: the engine only shows an item when its signal fired (or it's unconditional).
        return triggerFired(item.getTriggerCondition(), ctx.signals());
    }

    private boolean triggerFired(String condition, Set<String> signals) {
        if (condition == null || condition.isBlank() || "always".equalsIgnoreCase(condition)) {
            return true;
        }
        if ("first_time".equalsIgnoreCase(condition)) {
            return signals.contains("first_time");
        }
        return signals.contains(condition);
    }

    private boolean matchesReason(GuidanceItemEntity item, String reasonCode) {
        // Map common Tshepo/wallet deny-reason codes onto catalogue LOCKED_STATE items.
        String key = item.getGuidanceKey();
        return switch (reasonCode) {
            case "TRUST_TOO_LOW", "REQUIRES_VERIFICATION", "ASSURANCE_TOO_LOW" -> "UPGRADE_TRUST".equals(key);
            case "NOT_CHECKED_IN", "NO_WORKSPACE", "REQUIRES_FACILITY" -> "PROVIDER_CHECK_IN".equals(key);
            case "FACILITY_SETUP_INCOMPLETE" -> "FACILITY_SETUP".equals(key);
            default -> false;
        };
    }

    private boolean isSensitiveReason(String reasonCode) {
        return switch (reasonCode) {
            case "NO_CONSENT", "REQUIRES_CONSENT", "NO_DELEGATION", "NOT_AUTHORISED",
                 "CLINICAL_RESTRICTED", "CONSENT_REVOKED" -> true;
            default -> false;
        };
    }

    private String safeDefaultFor(String reasonCode) {
        return switch (reasonCode) {
            case "REQUIRES_CONSENT", "NO_CONSENT" ->
                    "This needs an active consent or legal basis. You can request access or ask the record owner to grant consent.";
            case "NO_DELEGATION" ->
                    "You can only act for someone else when you have an active delegation. Request access to act on their behalf.";
            case "REQUIRES_FACILITY", "NO_WORKSPACE", "NOT_CHECKED_IN" ->
                    "This action needs a facility or workspace context. Check in to the correct facility first.";
            case "REQUIRES_ROLE", "REQUIRES_SUPERVISOR" ->
                    "This action needs a specific role or supervisor approval. We can route you to someone who can help.";
            default ->
                    "This is restricted under current policy. We can explain the next step or connect you with support.";
        };
    }

    private boolean notWildcard(String value) {
        return value != null && !value.isBlank() && !"*".equals(value);
    }

    private Map<String, Object> toView(GuidanceItemEntity i) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("key", i.getGuidanceKey());
        v.put("domain", i.getDomain());
        v.put("type", i.getGuidanceType());
        v.put("title", i.getTitle());
        v.put("body", i.getBody());
        v.put("ctaLabel", i.getCtaLabel());
        v.put("ctaRoute", i.getCtaRoute());
        v.put("ownerService", i.getOwnerService());
        v.put("severity", i.getSeverity());
        v.put("priority", i.getPriority());
        v.put("khulumaFollowUpAvailable", i.isKhulumaFollowUp());
        v.put("fundoContentRef", i.getFundoContentRef());
        v.put("auditRequired", i.isAuditRequired());
        v.put("i18nKey", i.getI18nKey());
        v.put("accessibilityNote", i.getAccessibilityNote());
        return v;
    }

    // ── dismissal ────────────────────────────────────────────────────────────────────

    @Transactional
    public void dismiss(String tenantId, String actorId, String guidanceKey) {
        if (dismissalRepo.existsByTenantIdAndActorIdAndGuidanceKey(tenantId, actorId, guidanceKey)) {
            return;
        }
        GuidanceDismissalEntity d = new GuidanceDismissalEntity();
        d.setTenantId(tenantId);
        d.setActorId(actorId);
        d.setGuidanceKey(guidanceKey);
        dismissalRepo.save(d);
        log.debug("Nompilo guidance dismissed: tenant={} actor={} key={}", tenantId, actorId, guidanceKey);
    }
}
