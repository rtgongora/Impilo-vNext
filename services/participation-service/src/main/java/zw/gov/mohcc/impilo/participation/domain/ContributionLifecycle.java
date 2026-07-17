package zw.gov.mohcc.impilo.participation.domain;

import java.util.Map;
import java.util.Set;

/**
 * Contribution state machine. Reflects the co-design operating cycle
 * Receive → Community review → Explore → Research → Select for co-design → Plan → Build →
 * Ready for testing → Release, with Not-feasible and Merge as off-ramps.
 *
 * <p>The transition table is the single source of truth for what status changes are legal;
 * {@link #canTransition(String, String)} is enforced by {@code ContributionService} so
 * illegal jumps are rejected rather than silently applied.</p>
 */
public final class ContributionLifecycle {

    private ContributionLifecycle() {
    }

    public static final String RECEIVED = "RECEIVED";
    public static final String UNDER_COMMUNITY_REVIEW = "UNDER_COMMUNITY_REVIEW";
    public static final String BEING_EXPLORED = "BEING_EXPLORED";
    public static final String IN_RESEARCH = "IN_RESEARCH";
    public static final String SELECTED_FOR_CODESIGN = "SELECTED_FOR_CODESIGN";
    public static final String PLANNED = "PLANNED";
    public static final String IN_DEVELOPMENT = "IN_DEVELOPMENT";
    public static final String READY_FOR_TESTING = "READY_FOR_TESTING";
    public static final String RELEASED = "RELEASED";
    public static final String NOT_FEASIBLE = "NOT_FEASIBLE";
    public static final String MERGED = "MERGED";

    /** Statuses that end the delivery journey. */
    public static final Set<String> TERMINAL = Set.of(RELEASED, MERGED);

    /** MERGED and NOT_FEASIBLE can be reached from any non-terminal working status. */
    private static final Set<String> OFFRAMPS = Set.of(NOT_FEASIBLE, MERGED);

    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry(RECEIVED, with(UNDER_COMMUNITY_REVIEW)),
            Map.entry(UNDER_COMMUNITY_REVIEW, with(BEING_EXPLORED)),
            Map.entry(BEING_EXPLORED, with(IN_RESEARCH, SELECTED_FOR_CODESIGN)),
            Map.entry(IN_RESEARCH, with(SELECTED_FOR_CODESIGN)),
            Map.entry(SELECTED_FOR_CODESIGN, with(PLANNED)),
            Map.entry(PLANNED, with(IN_DEVELOPMENT)),
            Map.entry(IN_DEVELOPMENT, with(READY_FOR_TESTING)),
            Map.entry(READY_FOR_TESTING, with(RELEASED)),
            Map.entry(RELEASED, Set.of()),
            // A not-feasible contribution can be reopened for a fresh community look.
            Map.entry(NOT_FEASIBLE, Set.of(UNDER_COMMUNITY_REVIEW)),
            Map.entry(MERGED, Set.of()));

    private static Set<String> with(String... forward) {
        java.util.HashSet<String> s = new java.util.HashSet<>(OFFRAMPS);
        for (String f : forward) {
            s.add(f);
        }
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
