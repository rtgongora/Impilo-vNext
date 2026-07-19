package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Membership lifecycle (spec §10). Enforces the 13-status state machine, keeps
 * verification status on a SEPARATE axis (§10.2), and manages dependants (§10.3).
 *
 * <p>Invalid transitions throw {@link IllegalStateException} → surfaced as 409 by the
 * membership controller; unknown targets throw {@link IllegalArgumentException} → 400.
 */
@Service
public class MembershipService {

    /** Legal membership status transitions (spec §10.2). Terminal states have no outgoing edges. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("DRAFT", Set.of("DECLARED", "PENDING_VERIFICATION", "CANCELLED", "ENTERED_IN_ERROR")),
            Map.entry("DECLARED", Set.of("PENDING_VERIFICATION", "VERIFIED", "ACTIVE", "DISPUTED", "CANCELLED", "ENTERED_IN_ERROR")),
            Map.entry("PENDING_VERIFICATION", Set.of("VERIFIED", "ACTIVE", "DISPUTED", "CANCELLED", "ENTERED_IN_ERROR")),
            Map.entry("VERIFIED", Set.of("ACTIVE", "SUSPENDED", "TERMINATED", "DISPUTED")),
            Map.entry("ACTIVE", Set.of("SUSPENDED", "GRACE_PERIOD", "EXPIRED", "TERMINATED", "CANCELLED", "DISPUTED", "MERGED")),
            Map.entry("SUSPENDED", Set.of("ACTIVE", "TERMINATED", "CANCELLED", "EXPIRED")),
            Map.entry("GRACE_PERIOD", Set.of("ACTIVE", "EXPIRED", "TERMINATED")),
            Map.entry("DISPUTED", Set.of("VERIFIED", "ACTIVE", "TERMINATED", "CANCELLED")),
            Map.entry("EXPIRED", Set.of("ACTIVE", "TERMINATED")),
            // Terminal
            Map.entry("TERMINATED", Set.of()),
            Map.entry("CANCELLED", Set.of()),
            Map.entry("MERGED", Set.of()),
            Map.entry("ENTERED_IN_ERROR", Set.of()));

    private static final Set<String> ALL_STATUSES = TRANSITIONS.keySet();

    private static final Set<String> DEPENDANT_RELATIONSHIPS = Set.of(
            "SPOUSE", "PARTNER", "CHILD", "ADOPTED_CHILD", "STEPCHILD", "PARENT",
            "DISABLED_ADULT_DEPENDANT", "OTHER_DEPENDANT");

    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final CoverageEventService eventService;

    public MembershipService(MemberCoverageRepository memberRepository,
                             CoveragePlanRepository planRepository,
                             CoverageEventService eventService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public MemberCoverageEntity get(UUID tenantId, UUID membershipId) {
        return memberRepository.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found: " + membershipId));
    }

    @Transactional(readOnly = true)
    public List<MemberCoverageEntity> listDependants(UUID tenantId, UUID principalId) {
        return memberRepository.findByTenantIdAndPrincipalMemberId(tenantId, principalId);
    }

    @Transactional(readOnly = true)
    public List<MemberCoverageEntity> pendingVerification(UUID tenantId) {
        return memberRepository.findByTenantIdAndVerificationStatus(tenantId, "PENDING_VERIFICATION");
    }

    /**
     * Record a verification outcome. Verification status moves on its own axis; a positive
     * verification also advances membership status to VERIFIED when it is still pre-active.
     */
    @Transactional
    public MemberCoverageEntity verify(UUID tenantId, String podId, UUID membershipId,
                                       String source, boolean verified, UUID correlationId) {
        MemberCoverageEntity m = get(tenantId, membershipId);
        m.setVerificationStatus(verified ? "VERIFIED" : "DISPUTED");
        m.setVerificationDate(OffsetDateTime.now());
        m.setVerificationSource(source != null && !source.isBlank() ? source : "REGISTRY_ATTESTED");
        if (verified && Set.of("DRAFT", "DECLARED", "PENDING_VERIFICATION").contains(m.getStatus())) {
            applyTransition(m, "VERIFIED");
        }
        memberRepository.save(m);
        eventService.emitCoverageLifecycle(verified ? "verified" : "disputed", m.getId(), correlationId,
                tenantId, podId, snapshot(m));
        return m;
    }

    @Transactional
    public MemberCoverageEntity transition(UUID tenantId, String podId, UUID membershipId,
                                           String targetStatus, String reason, UUID correlationId) {
        MemberCoverageEntity m = get(tenantId, membershipId);
        String target = targetStatus == null ? "" : targetStatus.trim().toUpperCase();
        applyTransition(m, target);
        if ("TERMINATED".equals(target) && reason != null) {
            m.setTerminationReason(reason);
        }
        memberRepository.save(m);
        eventService.emitCoverageLifecycle(lifecycleAction(target), m.getId(), correlationId,
                tenantId, podId, snapshot(m));
        return m;
    }

    /** Enrol a dependant under a principal membership (spec §10.3). */
    @Transactional
    public MemberCoverageEntity addDependant(UUID tenantId, String podId, UUID principalId,
                                             String dependantClientId, String relationship,
                                             String memberNumber, UUID correlationId) {
        MemberCoverageEntity principal = get(tenantId, principalId);
        if (principal.getPrincipalMemberId() != null) {
            throw new IllegalArgumentException("Cannot add a dependant under another dependant");
        }
        String rel = relationship == null ? "" : relationship.trim().toUpperCase();
        if (!DEPENDANT_RELATIONSHIPS.contains(rel)) {
            throw new IllegalArgumentException("Invalid dependant relationship: " + relationship);
        }
        if (dependantClientId == null || dependantClientId.isBlank()) {
            throw new IllegalArgumentException("dependantClientId is required");
        }
        CoveragePlanEntity plan = planRepository.findById(principal.getPlanId()).orElse(null);
        MemberCoverageEntity dependant = new MemberCoverageEntity(
                tenantId, podId, dependantClientId.trim(), principal.getPlanId(),
                memberNumber, rel, LocalDate.now());
        dependant.setPrincipalMemberId(principal.getId());
        dependant.setStatus("ACTIVE");
        dependant.setVerificationStatus(principal.getVerificationStatus());
        dependant.setCoveragePriority(principal.getCoveragePriority());
        memberRepository.save(dependant);

        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("principal_member_id", principal.getId().toString());
        payload.put("client_id", dependant.getClientId());
        payload.put("relationship", rel);
        payload.put("plan_code", plan != null ? plan.getPlanCode() : null);
        eventService.emitCoverageLifecycle("dependant-added", dependant.getId(), correlationId,
                tenantId, podId, payload);
        return dependant;
    }

    private void applyTransition(MemberCoverageEntity m, String target) {
        if (!ALL_STATUSES.contains(target)) {
            throw new IllegalArgumentException("Unknown membership status: " + target);
        }
        String current = m.getStatus();
        if (current.equals(target)) {
            return; // idempotent no-op
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Illegal membership transition " + current + " -> " + target);
        }
        m.setStatus(target);
    }

    private static String lifecycleAction(String status) {
        return switch (status) {
            case "ACTIVE" -> "activated";
            case "SUSPENDED" -> "suspended";
            case "TERMINATED" -> "terminated";
            case "VERIFIED" -> "verified";
            case "CANCELLED" -> "cancelled";
            default -> status.toLowerCase();
        };
    }

    private static Map<String, Object> snapshot(MemberCoverageEntity m) {
        java.util.LinkedHashMap<String, Object> s = new java.util.LinkedHashMap<>();
        s.put("membership_id", m.getId().toString());
        s.put("client_id", m.getClientId());
        s.put("status", m.getStatus());
        s.put("verification_status", m.getVerificationStatus());
        s.put("verification_source", m.getVerificationSource());
        return s;
    }
}
