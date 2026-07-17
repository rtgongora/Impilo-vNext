package zw.gov.mohcc.impilo.participation.domain;

import java.util.Set;

/**
 * Public-board visibility gate for a contribution. Only {@link #APPROVED} contributions
 * are shown on the public idea board.
 */
public final class ModerationState {

    private ModerationState() {
    }

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public static final Set<String> ALL = Set.of(PENDING, APPROVED, REJECTED);

    public static boolean isKnown(String state) {
        return state != null && ALL.contains(state);
    }
}
