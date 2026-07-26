package zw.gov.mohcc.impilo.clinical.contraception;

import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Medical eligibility for contraceptive methods: a matrix, resolved per method.
 *
 * <p>Pure and static. Contains no clinical content — the matrix is supplied — so the licence status
 * of WHO material never touches this class, and a ministry-authored replacement matrix needs no code
 * change.
 *
 * <p><b>Why a matrix engine rather than the rule engine we already have.</b> Three properties defeat
 * both a rule list and a classification table:
 *
 * <ul>
 *   <li>It is methods × conditions. As rules, every condition predicate would be duplicated once per
 *       method — twenty methods and a hundred conditions is two thousand rules, and a threshold
 *       change becomes twenty edits that can disagree.</li>
 *   <li>Resolution is <b>max over conditions</b>, not first-match down an ordered list. Two
 *       conditions can both raise a method and both matter; a classification table returns one row
 *       and discards the rest.</li>
 *   <li>Cells are split initiation versus continuation, and they differ. A woman already using a
 *       method who develops a condition must be assessed on continuation, because for several
 *       conditions stopping is more harmful than continuing.</li>
 * </ul>
 *
 * <p><b>The unknown arithmetic is the safety property.</b> An unresolved condition whose cell is no
 * worse than the resolved category cannot change the answer and is not reported — otherwise every
 * result is provisional forever and clinicians learn to ignore the flag. But if the resolved
 * category is 1 or 2 and any unresolved condition maps to 3 or 4, the answer is
 * {@link Recommendation#CANNOT_DETERMINE}, never "use it", and the engine names the one question
 * that would settle it. A green light is never issued from an unasked screening question.
 */
public final class MedicalEligibilityEngine {

    private MedicalEligibilityEngine() {
    }

    /** Whether the woman is starting this method or already using it. */
    public enum UsePhase { INITIATION, CONTINUATION }

    public enum Recommendation {
        /** Category 1 — no restriction. */
        USE,
        /** Category 2 — advantages generally outweigh risks; use with follow-up. */
        USE_WITH_FOLLOW_UP,
        /**
         * Category 3. Deliberately not rendered as a number: MEC 3 means "use is not usually
         * recommended unless other more appropriate methods are not available or acceptable",
         * which is a judgement about the alternatives rather than a threshold. Shown as a
         * category alone, a UI renders it as a slightly worse 2.
         */
        SPECIALIST_JUDGEMENT_REQUIRED,
        /** Category 4 — unacceptable health risk. */
        DO_NOT_USE,
        /** An unasked question could raise this method to 3 or 4. Never a green light. */
        CANNOT_DETERMINE
    }

    /** A condition that actually moved this method's category, with the reason it did. */
    public record DrivingCondition(
            String conditionCode,
            String conditionName,
            int category,
            String clarification,
            List<String> sourceRefs) {
    }

    public record Eligibility(
            String methodCode,
            String methodName,
            UsePhase usePhase,
            Integer category,
            Recommendation recommendation,
            List<DrivingCondition> drivingConditions,
            List<String> unresolvedConditions,
            List<String> missingInputs,
            boolean provisional,
            String rationale) {
    }

    public record Assessment(
            List<Eligibility> methods,
            List<String> conditionsNotAssessed,
            List<String> conditionsOutOfScope,
            List<String> contentProblems,
            String contentVersion,
            String approvalStatus,
            String note) {
    }

    /**
     * Assess every method in the matrix, or a named subset.
     *
     * @param methodFilter method codes to assess; null or empty assesses all
     */
    public static Assessment assess(MecMatrix matrix, UsePhase usePhase,
                                    Map<String, Object> facts, List<String> methodFilter) {
        if (matrix == null) {
            return new Assessment(List.of(), List.of(), List.of(),
                    List.of("No medical eligibility matrix is loaded."), null, null,
                    "Eligibility could not be assessed: the criteria content is unavailable. "
                            + "This is not a statement that any method is safe.");
        }

        // Evaluate each condition once, not once per method.
        List<String> present = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        Set<String> missingInputs = new LinkedHashSet<>();
        List<String> contentProblems = new ArrayList<>();

        for (MecMatrix.Condition condition : matrix.conditions()) {
            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(condition.logic(), facts);
            if (result.hasContentErrors()) {
                contentProblems.addAll(result.contentErrors());
                // A defective condition is unresolved, never absent.
                unresolved.add(condition.code());
                missingInputs.addAll(condition.requiredInputs());
                continue;
            }
            switch (result.outcome()) {
                case TRUE -> present.add(condition.code());
                case UNKNOWN -> {
                    unresolved.add(condition.code());
                    missingInputs.addAll(result.missingInputs().isEmpty()
                            ? condition.requiredInputs() : result.missingInputs());
                }
                case FALSE -> { /* she does not have it; nothing to carry */ }
            }
        }

        List<Eligibility> results = new ArrayList<>();
        for (MecMatrix.Method method : matrix.methods()) {
            if (methodFilter != null && !methodFilter.isEmpty()
                    && !methodFilter.contains(method.code())) {
                continue;
            }
            results.add(resolve(matrix, method, usePhase, present, unresolved));
        }

        return new Assessment(
                List.copyOf(results),
                List.copyOf(unresolved),
                List.copyOf(matrix.conditionsOutOfScope()),
                List.copyOf(contentProblems),
                matrix.version(),
                matrix.approvalStatus(),
                matrix.coverageNote());
    }

    private static Eligibility resolve(MecMatrix matrix, MecMatrix.Method method, UsePhase usePhase,
                                       List<String> present, List<String> unresolved) {
        int resolved = 1;
        List<DrivingCondition> drivers = new ArrayList<>();

        for (String conditionCode : present) {
            MecMatrix.Cell cell = matrix.cell(conditionCode, method.code());
            if (cell == null) {
                // The content does not speak to this pair. Not the same as category 1, and treated
                // as unresolved rather than silently ignored.
                continue;
            }
            int category = cell.categoryFor(usePhase);
            if (category > resolved) {
                resolved = category;
            }
            if (category >= 2) {
                drivers.add(new DrivingCondition(conditionCode,
                        matrix.conditionName(conditionCode), category,
                        cell.clarification(), cell.sourceRefs()));
            }
        }

        // A pair with no cell is a hole in the content, and a hole must be visible.
        List<String> unclassified = new ArrayList<>();
        for (String conditionCode : present) {
            if (matrix.cell(conditionCode, method.code()) == null) {
                unclassified.add(conditionCode);
            }
        }

        // Which unanswered questions could still change this answer? Only those whose cell is worse
        // than what we have already resolved. Reporting the rest would make every result provisional
        // and train people to ignore the flag.
        List<String> blocking = new ArrayList<>();
        int worstPossible = resolved;
        for (String conditionCode : unresolved) {
            MecMatrix.Cell cell = matrix.cell(conditionCode, method.code());
            if (cell == null) {
                continue;
            }
            int category = cell.categoryFor(usePhase);
            if (category > resolved) {
                blocking.add(conditionCode);
                worstPossible = Math.max(worstPossible, category);
            }
        }

        boolean provisional = !blocking.isEmpty() || !unclassified.isEmpty();
        Recommendation recommendation = recommend(resolved, worstPossible, drivers, unclassified);

        return new Eligibility(
                method.code(), method.name(), usePhase,
                recommendation == Recommendation.CANNOT_DETERMINE ? null : resolved,
                recommendation,
                List.copyOf(drivers),
                List.copyOf(blocking),
                List.copyOf(matrix.requiredInputsFor(blocking)),
                provisional,
                rationale(method, resolved, worstPossible, drivers, blocking, unclassified,
                        recommendation));
    }

    private static Recommendation recommend(int resolved, int worstPossible,
                                            List<DrivingCondition> drivers,
                                            List<String> unclassified) {
        // The rule that matters: a green light is never issued from an unasked question.
        if (resolved <= 2 && worstPossible >= 3) {
            return Recommendation.CANNOT_DETERMINE;
        }
        if (resolved <= 2 && !unclassified.isEmpty()) {
            return Recommendation.CANNOT_DETERMINE;
        }
        if (drivers.stream().anyMatch(d -> d.category() == 3) && resolved == 3) {
            return Recommendation.SPECIALIST_JUDGEMENT_REQUIRED;
        }
        return switch (resolved) {
            case 1 -> Recommendation.USE;
            case 2 -> Recommendation.USE_WITH_FOLLOW_UP;
            case 3 -> Recommendation.SPECIALIST_JUDGEMENT_REQUIRED;
            default -> Recommendation.DO_NOT_USE;
        };
    }

    private static String rationale(MecMatrix.Method method, int resolved, int worstPossible,
                                    List<DrivingCondition> drivers, List<String> blocking,
                                    List<String> unclassified, Recommendation recommendation) {
        StringBuilder text = new StringBuilder();
        if (drivers.isEmpty()) {
            text.append("No condition assessed so far restricts ").append(method.name()).append(".");
        } else {
            DrivingCondition worst = drivers.stream()
                    .max((a, b) -> Integer.compare(a.category(), b.category())).orElseThrow();
            text.append(worst.conditionName()).append(" places ").append(method.name())
                    .append(" at category ").append(worst.category()).append('.');
        }
        if (!unclassified.isEmpty()) {
            text.append(" The criteria do not cover ").append(String.join(", ", unclassified))
                    .append(" for this method, which is not the same as no restriction.");
        }
        if (!blocking.isEmpty()) {
            text.append(" ").append(String.join(", ", blocking))
                    .append(" was not assessed and would place it at category ").append(worstPossible)
                    .append(". Ask before recommending.");
        }
        if (recommendation == Recommendation.SPECIALIST_JUDGEMENT_REQUIRED) {
            text.append(" Category 3 is a judgement about what else is available and acceptable, "
                    + "not a threshold.");
        }
        return text.toString();
    }
}
