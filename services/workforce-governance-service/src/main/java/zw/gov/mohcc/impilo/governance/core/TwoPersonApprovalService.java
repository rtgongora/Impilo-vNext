package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.PlatformActionApprovalEntity;
import zw.gov.mohcc.impilo.governance.persistence.PlatformActionApprovalRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enforces the two-person (multi-party) approval rule on access requests.
 *
 * <p>The {@code approvals_required} JSON on an {@link AccessRequestEntity} is a
 * list of role requirements (e.g. {@code ["PLATFORM_ORIGIN_ADMINISTRATOR",
 * "PLATFORM_ORIGIN_ADMINISTRATOR"]}; entries may also be objects with a
 * {@code role} key). When the list is non-empty, transitioning the request to
 * APPROVED requires at least N distinct approver user IDs — one per listed
 * requirement, each satisfying that requirement's role ({@code ANY}/{@code *}
 * matches any role) — and no approver may be the request initiator.</p>
 *
 * <p>Backward compatible: when {@code approvals_required} is null/blank/empty,
 * the legacy single-approver path is unchanged and this service imposes no
 * additional constraint.</p>
 */
@Service
public class TwoPersonApprovalService {

    private static final Logger log = LoggerFactory.getLogger(TwoPersonApprovalService.class);

    static final String DECISION_APPROVE = "APPROVE";
    static final String DECISION_REJECT = "REJECT";

    private final PlatformActionApprovalRepository approvalRepository;
    private final ObjectMapper objectMapper;

    public TwoPersonApprovalService(PlatformActionApprovalRepository approvalRepository,
                                    ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.objectMapper = objectMapper;
    }

    /** True when the request carries a non-empty approvals_required list (multi-party rule applies). */
    public boolean requiresMultiPartyApproval(AccessRequestEntity request) {
        return !parseRequirements(request.getApprovalsRequired()).isEmpty();
    }

    /**
     * Record one approver's decision. Enforces approver != initiator and one
     * decision per approver (duplicate approvers are rejected).
     */
    @Transactional
    public PlatformActionApprovalEntity recordApproval(AccessRequestEntity request,
                                                       String approverUserId,
                                                       String approverRole,
                                                       String decision,
                                                       String note) {
        if (approverUserId == null || approverUserId.isBlank()) {
            throw new IllegalArgumentException("approverUserId is required");
        }
        if (approverUserId.equals(request.getRequesterId())) {
            throw new IllegalArgumentException(
                    "Two-person rule: the initiator of a platform action cannot approve it");
        }
        if (approvalRepository.existsByAccessRequestIdAndApproverUserId(request.getId(), approverUserId)) {
            throw new IllegalArgumentException(
                    "Approver " + approverUserId + " has already recorded a decision for this request");
        }
        String normalisedDecision = decision == null || decision.isBlank()
                ? DECISION_APPROVE : decision.toUpperCase(Locale.ROOT);
        if (!DECISION_APPROVE.equals(normalisedDecision) && !DECISION_REJECT.equals(normalisedDecision)) {
            throw new IllegalArgumentException("Unknown approval decision: " + decision);
        }
        PlatformActionApprovalEntity entity = new PlatformActionApprovalEntity();
        entity.setAccessRequestId(request.getId());
        entity.setApproverUserId(approverUserId);
        entity.setApproverRole(approverRole);
        entity.setDecision(normalisedDecision);
        entity.setNote(note);
        PlatformActionApprovalEntity saved = approvalRepository.save(entity);
        log.info("Recorded {} by approver {} on access request {}",
                normalisedDecision, approverUserId, request.getId());
        return saved;
    }

    /**
     * True when the request's approvals_required list is satisfied: each listed
     * role requirement is covered by a distinct APPROVE decision from an
     * approver who is not the initiator. Empty/null requirements → always true
     * (legacy single-approver path).
     */
    @Transactional(readOnly = true)
    public boolean isSatisfied(AccessRequestEntity request) {
        List<String> requirements = parseRequirements(request.getApprovalsRequired());
        if (requirements.isEmpty()) {
            return true;
        }
        List<PlatformActionApprovalEntity> approvals = approvalRepository
                .findByAccessRequestIdOrderByDecidedAtAsc(request.getId()).stream()
                .filter(a -> DECISION_APPROVE.equalsIgnoreCase(a.getDecision()))
                .filter(a -> !a.getApproverUserId().equals(request.getRequesterId()))
                .toList();
        return matchesRequirements(requirements, approvals);
    }

    /**
     * Gate used by {@code AccessRequestService.transition}: an APPROVED
     * transition on a request with non-empty approvals_required demands the
     * multi-party rule be satisfied first.
     */
    public void assertTransitionAllowed(AccessRequestEntity request, String newStatus) {
        if (!"APPROVED".equalsIgnoreCase(newStatus)) {
            return;
        }
        List<String> requirements = parseRequirements(request.getApprovalsRequired());
        if (requirements.isEmpty()) {
            return; // legacy single-approver path — unchanged
        }
        if (!isSatisfied(request)) {
            throw new IllegalStateException(
                    "Access request " + request.getId() + " requires " + requirements.size()
                    + " distinct approvals (" + String.join(", ", requirements)
                    + ") before it can be APPROVED");
        }
    }

    /** Count of recorded APPROVE decisions (excluding any by the initiator). */
    @Transactional(readOnly = true)
    public long approvalCount(AccessRequestEntity request) {
        return approvalRepository.findByAccessRequestIdOrderByDecidedAtAsc(request.getId()).stream()
                .filter(a -> DECISION_APPROVE.equalsIgnoreCase(a.getDecision()))
                .filter(a -> !a.getApproverUserId().equals(request.getRequesterId()))
                .count();
    }

    @Transactional(readOnly = true)
    public List<PlatformActionApprovalEntity> approvals(AccessRequestEntity request) {
        return approvalRepository.findByAccessRequestIdOrderByDecidedAtAsc(request.getId());
    }

    /**
     * Greedy bipartite match: consume each requirement with a distinct approval
     * whose role satisfies it. Specific-role requirements are matched before
     * ANY-role requirements so wildcards do not starve them.
     */
    private static boolean matchesRequirements(List<String> requirements,
                                               List<PlatformActionApprovalEntity> approvals) {
        List<String> ordered = new ArrayList<>(requirements);
        ordered.sort((a, b) -> Boolean.compare(isAnyRole(a), isAnyRole(b)));
        boolean[] used = new boolean[approvals.size()];
        for (String requirement : ordered) {
            boolean covered = false;
            for (int i = 0; i < approvals.size(); i++) {
                if (used[i]) continue;
                if (roleSatisfies(requirement, approvals.get(i).getApproverRole())) {
                    used[i] = true;
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAnyRole(String requirement) {
        return requirement == null || requirement.isBlank()
                || "ANY".equalsIgnoreCase(requirement) || "*".equals(requirement);
    }

    private static boolean roleSatisfies(String requirement, String approverRole) {
        if (isAnyRole(requirement)) {
            return true;
        }
        return requirement.equalsIgnoreCase(approverRole);
    }

    /**
     * Parse approvals_required JSON into a list of role requirement strings.
     * Accepts an array of strings, or an array of objects with a {@code role}
     * key. Null/blank/empty/unparseable → empty list (legacy path).
     */
    List<String> parseRequirements(String approvalsRequiredJson) {
        if (approvalsRequiredJson == null || approvalsRequiredJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(approvalsRequiredJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode entry : node) {
                if (entry.isTextual()) {
                    out.add(entry.asText());
                } else if (entry.isObject() && entry.hasNonNull("role")) {
                    out.add(entry.get("role").asText());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("Unparseable approvals_required JSON — treating as legacy single-approver: {}",
                    e.getMessage());
            return List.of();
        }
    }
}
