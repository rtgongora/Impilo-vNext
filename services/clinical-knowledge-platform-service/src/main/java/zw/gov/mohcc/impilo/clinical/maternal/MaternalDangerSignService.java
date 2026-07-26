package zw.gov.mohcc.impilo.clinical.maternal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Danger signs across the maternal pathway — early pregnancy, antenatal, labour, postnatal.
 *
 * <p>One service over several content packs rather than one service per pack. The packs differ in
 * their rules; the safety properties are identical, and three copies of these properties would
 * eventually be two.
 *
 * <p><b>This service deliberately does NOT require the woman's age.</b> Its paediatric sibling,
 * {@code DangerSignEvaluationService}, refuses to assess at all without {@code ageDays}, and that is
 * right there — every paediatric danger sign is age-scoped and applying the wrong set is worse than
 * applying none. Inheriting it here would be the same error reversed: refusing to assess a bleeding
 * woman because her date of birth is missing. Maternal rules are scoped by gestation, parity and
 * postpartum day, not by how old she is.
 *
 * <p><b>An empty alert list is not "she is fine".</b> The result reports what was not assessed and
 * marks itself incomplete, and {@link Assessment#noDangerSignsFound()} is true only when screening
 * was actually completed. A caller that renders an empty list as reassurance is reading a fact that
 * is not there — which is how a woman with an unasked-about symptom goes home.
 */
@Service
public class MaternalDangerSignService {

    private static final Logger log = LoggerFactory.getLogger(MaternalDangerSignService.class);

    private static final List<String> SEVERITY_ORDER =
            List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final RuleContentLoader contentLoader;

    public MaternalDangerSignService(RuleContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    public record Alert(
            String code,
            String name,
            String severity,
            boolean interruptive,
            boolean overrideAllowed,
            boolean referralRequired,
            String message,
            String explanation,
            String requiredAction,
            String monitoringInstruction,
            List<String> triggeringFindings,
            List<String> sourceRefs,
            Map<String, Object> provenance) {
    }

    /**
     * @param topLineAction     the single action to lead with — the most severe alert's. One, because
     *                          four equally-weighted actions on a shocked woman is no priority at all
     * @param notAssessed       findings a rule needed and did not get
     * @param incomplete        true when any applicable rule could not be resolved
     * @param screeningComplete what the CALLER asserted about whether screening was finished; never
     *                          inferred from an empty alert list
     */
    public record Assessment(
            List<Alert> alerts,
            String topLineAction,
            List<String> notAssessed,
            List<String> contentProblems,
            boolean incomplete,
            boolean screeningComplete,
            String contentVersion,
            String approvalStatus,
            String note) {

        /**
         * True only when screening was completed AND nothing fired. Deliberately a method rather
         * than a bare empty-list check at the call site: "no alerts" and "no danger signs" are
         * different statements, and only one of them is safe to show a clinician.
         */
        public boolean noDangerSignsFound() {
            return screeningComplete && alerts.isEmpty() && !incomplete;
        }

        static Assessment unavailable(String why) {
            return new Assessment(List.of(), null, List.of(), List.of(why), true, false,
                    null, null, why);
        }
    }

    /**
     * Evaluate a pack.
     *
     * @param screeningComplete the caller's explicit assertion that the screening questions were
     *                          asked. Passed in rather than derived, because the only thing that
     *                          knows whether a form was completed is the thing that presented it.
     */
    public Assessment evaluate(String resourcePath, Map<String, Object> facts,
                               boolean screeningComplete) {
        List<TabularRule> rules = contentLoader.load(resourcePath);
        if (rules.isEmpty()) {
            return Assessment.unavailable("Maternal danger-sign content is not loaded from "
                    + resourcePath + ", so no assessment was made. This is not a statement that "
                    + "there are no danger signs.");
        }

        Map<String, Object> workingFacts = new LinkedHashMap<>(facts == null ? Map.of() : facts);

        List<Alert> alerts = new ArrayList<>();
        Set<String> notAssessed = new LinkedHashSet<>();
        List<String> contentProblems = new ArrayList<>();
        boolean incomplete = false;

        for (TabularRule rule : rules) {
            // Applicability first. A rule that does not apply to this woman contributes nothing —
            // neither an alert nor an unassessed finding, because asking for its inputs would send
            // a clinician looking for observations that are irrelevant to her.
            Applicability applicability = applicability(rule, workingFacts, contentProblems);
            if (applicability == Applicability.NOT_APPLICABLE) {
                continue;
            }
            if (applicability == Applicability.UNDECIDABLE) {
                // We cannot tell whether it applies. That is not the same as it not applying, and
                // the inputs that would settle it are worth asking for.
                notAssessed.addAll(rule.requiredInputs());
                incomplete = true;
                continue;
            }

            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(rule.logic(), workingFacts);
            if (result.hasContentErrors()) {
                contentProblems.addAll(result.contentErrors());
                incomplete = true;
                continue;
            }
            switch (result.outcome()) {
                case TRUE -> alerts.add(toAlert(rule, result));
                case UNKNOWN -> {
                    notAssessed.addAll(result.missingInputs().isEmpty()
                            ? rule.requiredInputs() : result.missingInputs());
                    incomplete = true;
                }
                case FALSE -> { /* asked, and absent */ }
            }
        }

        alerts.sort(Comparator.comparingInt(a -> severityRank(a.severity())));

        return new Assessment(
                List.copyOf(alerts),
                alerts.isEmpty() ? null : alerts.get(0).requiredAction(),
                List.copyOf(notAssessed),
                List.copyOf(contentProblems),
                incomplete,
                screeningComplete,
                rules.get(0).contentVersion(),
                rules.get(0).approvalStatus(),
                note(alerts, notAssessed, incomplete, screeningComplete));
    }

    private enum Applicability { APPLIES, NOT_APPLICABLE, UNDECIDABLE }

    private static Applicability applicability(TabularRule rule, Map<String, Object> facts,
                                               List<String> contentProblems) {
        if (rule.appliesWhen() == null) {
            return Applicability.APPLIES;
        }
        PredicateEvaluator.Result gate = PredicateEvaluator.evaluate(rule.appliesWhen(), facts);
        if (gate.hasContentErrors()) {
            contentProblems.addAll(gate.contentErrors());
            return Applicability.UNDECIDABLE;
        }
        return switch (gate.outcome()) {
            case TRUE -> Applicability.APPLIES;
            case FALSE -> Applicability.NOT_APPLICABLE;
            case UNKNOWN -> Applicability.UNDECIDABLE;
        };
    }

    private static Alert toAlert(TabularRule rule, PredicateEvaluator.Result result) {
        return new Alert(
                rule.code(), rule.name(), rule.severity(),
                rule.interruptive(), rule.overrideAllowed(), rule.referralRequired(),
                rule.message(), rule.explanation(), rule.requiredAction(),
                rule.monitoringInstruction(),
                List.copyOf(result.usedInputs()),
                rule.sourceRefs(),
                rule.provenance() == null ? Map.of() : rule.provenance().toMap());
    }

    private static int severityRank(String severity) {
        int index = SEVERITY_ORDER.indexOf(severity == null ? "" : severity.toUpperCase());
        return index < 0 ? SEVERITY_ORDER.size() : index;
    }

    private static String note(List<Alert> alerts, Set<String> notAssessed, boolean incomplete,
                               boolean screeningComplete) {
        if (!alerts.isEmpty() && incomplete) {
            return "Danger signs were found AND the screening is incomplete. Act on what is below, "
                    + "and finish the assessment — there may be more.";
        }
        if (!alerts.isEmpty()) {
            return "Act on the top-line action first.";
        }
        if (incomplete) {
            return "Nothing has been ruled out. " + notAssessed.size() + " finding(s) were not "
                    + "assessed, so this is not a clear result — it is an unfinished one.";
        }
        if (!screeningComplete) {
            return "No danger sign fired on what was recorded, but the screening was not marked "
                    + "complete. Do not read this as a clear assessment.";
        }
        return "Screening complete, and no danger sign was found on the recorded findings.";
    }
}
