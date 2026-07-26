package zw.gov.mohcc.impilo.pct.core.clinical;

import java.util.Set;

/** Closed vocabularies for problem links, mirroring the CHECKs in V103__problem_links.sql. */
public final class ProblemLinkVocabulary {

    private ProblemLinkVocabulary() {}

    public static final Set<String> LINK_TYPES = Set.of(
            "COMPLICATION_OF",
            "CAUSED_BY",
            "COMORBID_WITH",
            "EVIDENCED_BY",
            "TREATED_BY",
            "MONITORED_BY",
            "GOAL_FOR");

    public static final Set<String> TARGET_TYPES = Set.of(
            "PROBLEM",
            "OBSERVATION",
            "MEDICATION",
            "DIAGNOSTIC_ORDER",
            "PROCEDURE",
            "GOAL",
            "CARE_PLAN",
            "DOCUMENT");

    /** The only target type whose reference is a real foreign key in this database. */
    public static final String LOCAL_TARGET = "PROBLEM";
}
