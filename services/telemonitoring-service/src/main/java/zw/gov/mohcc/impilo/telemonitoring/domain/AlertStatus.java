package zw.gov.mohcc.impilo.telemonitoring.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Guarded AlertEpisode state machine (§14.6 minimum schema):
 *
 * <pre>
 * OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED
 * OPEN/ACKNOWLEDGED/IN_PROGRESS -> ESCALATED (a recorded ladder rung is mandatory)
 * ESCALATED -> IN_PROGRESS | RESOLVED
 * </pre>
 *
 * RESOLVED is terminal and reachable ONLY through the accountable-closure record
 * (reviewer + assessment + action + outcome — §14.6 closure invariant, enforced in
 * the service). Bulk-close and auto-vanish do not exist.
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    ESCALATED,
    RESOLVED;

    private static final Map<AlertStatus, Set<AlertStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(ACKNOWLEDGED, ESCALATED),
            ACKNOWLEDGED, EnumSet.of(IN_PROGRESS, ESCALATED, RESOLVED),
            IN_PROGRESS, EnumSet.of(ESCALATED, RESOLVED),
            ESCALATED, EnumSet.of(IN_PROGRESS, RESOLVED),
            RESOLVED, EnumSet.noneOf(AlertStatus.class));

    public static boolean canTransition(AlertStatus from, AlertStatus to) {
        return from != null && to != null && ALLOWED.get(from).contains(to);
    }

    public boolean isLive() {
        return this != RESOLVED;
    }
}
