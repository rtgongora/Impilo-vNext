package zw.gov.mohcc.impilo.clinical.classification;

import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates IMNCI assess-and-classify tables.
 *
 * <p>The whole engine turns on one rule, and it is the reason this is not a switch statement:</p>
 *
 * <p><strong>A severity row that cannot be excluded blocks every row beneath it.</strong> If it is
 * unknown whether a coughing child has chest indrawing, that child cannot be classified as
 * "cough or cold". The severe row was not ruled out; it was never looked at. Falling through to
 * the reassuring row would convert a missing examination into a green classification and send a
 * child home. So the engine reports the outcome as provisional, names the row it could not
 * exclude, and states the observation needed to resolve it.</p>
 *
 * <p>This is the classification-table form of the principle the danger-sign engine applies to
 * individual signs: silence is not a negative finding. It matters more here, because a
 * classification carries a disposition — the difference between admission and going home.</p>
 *
 * <p>The engine is pure. It holds no content of its own: the tables are governed artefacts loaded
 * from versioned content, so a change in clinical policy is a content change with fixtures, not a
 * code change.</p>
 */
public final class ClassificationEngine {

    private ClassificationEngine() {
    }

    public enum Status {
        /** A row matched, or every row above the default was definitively excluded. */
        CLASSIFIED,
        /** The screening question was answered no — this table does not apply to this child. */
        NOT_APPLICABLE,
        /** The screening question was never asked, so it is unknown whether the table applies. */
        NOT_ASSESSED,
        /**
         * A severity row could not be excluded, so no safe classification can be issued.
         * Distinct from NOT_ASSESSED: here the assessment started and stopped short.
         */
        INDETERMINATE
    }

    /**
     * @param provisional     true when a classification was reached but a more severe row above it
     *                        could not be excluded; the classification is a floor, not an answer
     * @param unresolvedTiers the severity rows that evaluated to unknown, most severe first
     * @param missingInputs   the observations that would resolve them
     */
    public record Outcome(
            String tableId,
            String axis,
            Status status,
            String classificationCode,
            String classificationName,
            String colour,
            boolean provisional,
            List<String> unresolvedTiers,
            List<String> missingInputs,
            String action,
            List<String> treatments,
            boolean referralRequired,
            boolean urgentReferral,
            List<String> waivedCriteria,
            String rationale) {

        public boolean actionable() {
            return status == Status.CLASSIFIED && !provisional;
        }
    }

    public static Outcome classify(ClassificationTable table, Integer ageDays, Map<String, Object> facts) {
        if (!table.appliesToAge(ageDays)) {
            return outcome(table, Status.NOT_APPLICABLE, null, false, List.of(), List.of(), List.of(),
                    ageDays == null
                            ? "Age is unknown, and every IMNCI table is age-scoped."
                            : "This table does not apply at " + ageDays + " days of age.");
        }

        // Does the table apply at all? "No cough" and "nobody asked about cough" are different
        // answers, and only the second leaves work outstanding.
        if (table.appliesWhen() != null && !table.appliesWhen().isNull()) {
            PredicateEvaluator.Result screening = PredicateEvaluator.evaluate(table.appliesWhen(), facts);
            switch (screening.outcome()) {
                case FALSE -> {
                    return outcome(table, Status.NOT_APPLICABLE, null, false, List.of(), List.of(), List.of(),
                            "The screening question for " + table.axis().toLowerCase() + " was answered no.");
                }
                case UNKNOWN -> {
                    return outcome(table, Status.NOT_ASSESSED, null, false, List.of(),
                            screening.missingInputs(), List.of(),
                            "The screening question was never asked, so it is unknown whether this "
                            + "child has " + table.axis().toLowerCase() + ".");
                }
                default -> { /* TRUE — the table applies; continue. */ }
            }
        }

        List<String> unresolved = new ArrayList<>();
        Set<String> missing = new LinkedHashSet<>();
        Set<String> waived = new LinkedHashSet<>();

        for (ClassificationTable.Tier tier : table.tiers()) {
            if (tier.isDefault()) {
                // The default row is reached only after every row above it was excluded. If any of
                // them was merely unknown, the reassuring row is not available.
                if (!unresolved.isEmpty()) {
                    return outcome(table, Status.INDETERMINATE, null, false, unresolved, List.copyOf(missing), waived,
                            "Cannot classify: " + describeUnresolved(unresolved) + " could not be "
                            + "excluded. Complete " + joinInputs(missing) + " before classifying. The "
                            + "absence of a finding that was never sought is not a negative finding.");
                }
                return outcome(table, Status.CLASSIFIED, tier, false, List.of(), List.of(), waived,
                        "Every more severe classification was definitively excluded.");
            }

            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(tier.logic(), facts);
            waived.addAll(result.waivedCriteria());
            switch (result.outcome()) {
                case TRUE -> {
                    // A matched row wins immediately, but if a MORE severe row above it was
                    // unresolved the answer is still a floor rather than a conclusion.
                    boolean provisional = !unresolved.isEmpty();
                    return outcome(table, Status.CLASSIFIED, tier, provisional, unresolved, List.copyOf(missing), waived,
                            provisional
                                    ? "Classified as " + tier.name() + ", but " + describeUnresolved(unresolved)
                                      + " could not be excluded, so this is the least severe classification "
                                      + "this child could have — not necessarily the right one."
                                    : "Signs meet the criteria for " + tier.name() + ".");
                }
                case UNKNOWN -> {
                    unresolved.add(tier.code());
                    missing.addAll(result.missingInputs());
                }
                default -> { /* FALSE — genuinely excluded; continue down the table. */ }
            }
        }

        // No default row in the content: the table ran out without classifying.
        if (!unresolved.isEmpty()) {
            return outcome(table, Status.INDETERMINATE, null, false, unresolved, List.copyOf(missing), waived,
                    "Cannot classify: " + describeUnresolved(unresolved) + " could not be excluded.");
        }
        return outcome(table, Status.NOT_APPLICABLE, null, false, List.of(), List.of(), waived,
                "No classification in this table applies.");
    }

    private static Outcome outcome(ClassificationTable table, Status status, ClassificationTable.Tier tier,
                                   boolean provisional, List<String> unresolved, List<String> missing,
                                   Collection<String> waived, String rationale) {
        return new Outcome(
                table.tableId(),
                table.axis(),
                status,
                tier == null ? null : tier.code(),
                tier == null ? null : tier.name(),
                tier == null ? null : tier.colour(),
                provisional,
                List.copyOf(unresolved),
                List.copyOf(missing),
                tier == null ? null : tier.action(),
                tier == null ? List.of() : tier.treatments(),
                tier != null && tier.referralRequired(),
                tier != null && tier.urgentReferral(),
                List.copyOf(waived),
                rationale);
    }

    private static String describeUnresolved(List<String> unresolved) {
        return unresolved.size() == 1
                ? unresolved.get(0)
                : String.join(" and ", unresolved);
    }

    private static String joinInputs(Set<String> missing) {
        return missing.isEmpty() ? "the outstanding assessment" : String.join(", ", missing);
    }
}
