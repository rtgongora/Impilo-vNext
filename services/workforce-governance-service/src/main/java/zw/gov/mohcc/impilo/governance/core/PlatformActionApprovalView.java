package zw.gov.mohcc.impilo.governance.core;

import zw.gov.mohcc.impilo.governance.persistence.PlatformActionApprovalEntity;

import java.time.Instant;

/**
 * Read-only projection of a single approver's decision on a PLATFORM_ACTION
 * access request (who-approved), for the two-person approval console. Carries
 * only the fields the UI needs — approver id/role, decision, note, timestamp —
 * and never exposes internal write/decision-engine state.
 */
public record PlatformActionApprovalView(
        String approverUserId,
        String approverRole,
        String decision,
        String note,
        Instant decidedAt) {

    public static PlatformActionApprovalView from(PlatformActionApprovalEntity entity) {
        return new PlatformActionApprovalView(
                entity.getApproverUserId(),
                entity.getApproverRole(),
                entity.getDecision(),
                entity.getNote(),
                entity.getDecidedAt());
    }
}
