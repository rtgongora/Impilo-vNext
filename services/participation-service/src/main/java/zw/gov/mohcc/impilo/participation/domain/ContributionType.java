package zw.gov.mohcc.impilo.participation.domain;

import java.util.Set;

/**
 * The kinds of citizen contribution the Participation service accepts.
 */
public final class ContributionType {

    private ContributionType() {
    }

    /** A proposal for something new or better. */
    public static final String IDEA = "IDEA";
    /** A description of an experience that could be improved. */
    public static final String EXPERIENCE = "EXPERIENCE";
    /** A request to join testing / early access. */
    public static final String TESTING_SIGNUP = "TESTING_SIGNUP";
    /** A request to join a community of practice. */
    public static final String COP_MEMBERSHIP = "COP_MEMBERSHIP";

    public static final Set<String> ALL = Set.of(IDEA, EXPERIENCE, TESTING_SIGNUP, COP_MEMBERSHIP);

    /** Types that a member of the public may open through the anonymous intake lane. */
    public static final Set<String> PUBLIC_TYPES = Set.of(IDEA, EXPERIENCE);

    public static boolean isKnown(String type) {
        return type != null && ALL.contains(type);
    }
}
