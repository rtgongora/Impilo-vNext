package zw.gov.mohcc.impilo.rito.domain;

import java.util.Map;
import java.util.Set;

/**
 * Rito case state machine. Lifecycle reflects the operating cycle
 * Listen → Classify → Triage → Assign → Investigate → Act → Verify → Close → Learn → Improve.
 *
 * <p>The transition table is the single source of truth for what status changes are legal.
 * {@link #canTransition(String, String)} is enforced by {@code CaseService}; illegal jumps
 * are rejected rather than silently applied.
 */
public final class CaseLifecycle {

    private CaseLifecycle() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String TRIAGED = "TRIAGED";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String INVESTIGATION_OPEN = "INVESTIGATION_OPEN";
    public static final String ACTION_REQUIRED = "ACTION_REQUIRED";
    public static final String ESCALATED = "ESCALATED";
    public static final String WAITING_ON_CLIENT = "WAITING_ON_CLIENT";
    public static final String WAITING_ON_FACILITY = "WAITING_ON_FACILITY";
    public static final String WAITING_ON_SUPERVISOR = "WAITING_ON_SUPERVISOR";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";
    public static final String REOPENED = "REOPENED";
    public static final String CANCELLED = "CANCELLED";

    /** Statuses considered terminal for SLA / due-date purposes. */
    public static final Set<String> TERMINAL = Set.of(RESOLVED, CLOSED, CANCELLED);

    /** Statuses an active worklist treats as "open". */
    public static final Set<String> OPEN = Set.of(
            SUBMITTED, ACKNOWLEDGED, TRIAGED, ASSIGNED, UNDER_REVIEW, INVESTIGATION_OPEN,
            ACTION_REQUIRED, ESCALATED, WAITING_ON_CLIENT, WAITING_ON_FACILITY,
            WAITING_ON_SUPERVISOR, REOPENED);

    private static final Set<String> WORKING_STATES = Set.of(
            UNDER_REVIEW, INVESTIGATION_OPEN, ACTION_REQUIRED, ESCALATED,
            WAITING_ON_CLIENT, WAITING_ON_FACILITY, WAITING_ON_SUPERVISOR);

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT, Set.of(SUBMITTED, CANCELLED)),
            Map.entry(SUBMITTED, Set.of(ACKNOWLEDGED, TRIAGED, CANCELLED, ESCALATED)),
            Map.entry(ACKNOWLEDGED, Set.of(TRIAGED, ASSIGNED, CANCELLED, ESCALATED)),
            Map.entry(TRIAGED, Set.of(ASSIGNED, UNDER_REVIEW, ESCALATED, CANCELLED)),
            Map.entry(ASSIGNED, Set.of(UNDER_REVIEW, INVESTIGATION_OPEN, ACTION_REQUIRED,
                    ESCALATED, WAITING_ON_CLIENT, WAITING_ON_FACILITY, WAITING_ON_SUPERVISOR,
                    RESOLVED, CANCELLED)),
            Map.entry(UNDER_REVIEW, working(RESOLVED)),
            Map.entry(INVESTIGATION_OPEN, working(RESOLVED)),
            Map.entry(ACTION_REQUIRED, working(RESOLVED)),
            Map.entry(ESCALATED, working(RESOLVED)),
            Map.entry(WAITING_ON_CLIENT, working(RESOLVED)),
            Map.entry(WAITING_ON_FACILITY, working(RESOLVED)),
            Map.entry(WAITING_ON_SUPERVISOR, working(RESOLVED)),
            Map.entry(RESOLVED, Set.of(CLOSED, REOPENED)),
            Map.entry(CLOSED, Set.of(REOPENED)),
            Map.entry(REOPENED, Set.of(UNDER_REVIEW, INVESTIGATION_OPEN, ACTION_REQUIRED,
                    ESCALATED, ASSIGNED, RESOLVED, CANCELLED)),
            Map.entry(CANCELLED, Set.of(REOPENED)));

    private static Set<String> working(String... extra) {
        java.util.HashSet<String> s = new java.util.HashSet<>(WORKING_STATES);
        for (String e : extra) {
            s.add(e);
        }
        s.add(CANCELLED);
        return Set.copyOf(s);
    }

    public static boolean isKnown(String status) {
        return TRANSITIONS.containsKey(status);
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.equals(to)) {
            return true;
        }
        Set<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
