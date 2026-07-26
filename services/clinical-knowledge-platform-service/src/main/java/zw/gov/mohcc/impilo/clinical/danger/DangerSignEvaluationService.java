package zw.gov.mohcc.impilo.clinical.danger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates paediatric danger signs — the ETAT emergency signs, the IMNCI general danger
 * signs, and the young-infant signs of possible serious bacterial infection.
 *
 * <p>These are the findings that mean a child needs treatment before the assessment
 * continues. The engine exists because recognising them is time-critical and the signs are
 * easy to miss under pressure, particularly in the first two months of life where they are
 * few and non-specific and deterioration is fast.</p>
 *
 * <p>Three properties are deliberate:</p>
 * <ul>
 *   <li><strong>Silence is never reassurance.</strong> A sign that was not assessed is
 *       reported as unassessed, and the result says plainly that it is incomplete. A clean
 *       result built on questions nobody asked is the most dangerous output this service
 *       could produce.</li>
 *   <li><strong>Emergency alerts cannot be dismissed.</strong> Where the content marks a rule
 *       non-overridable, the alert carries that through; an airway emergency is not a
 *       preference.</li>
 *   <li><strong>Every alert states its action and its source.</strong> A finding without a
 *       next step is a notification, and a recommendation without a citable origin and
 *       version is not something a clinician can be asked to act on.</li>
 * </ul>
 */
@Service
public class DangerSignEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(DangerSignEvaluationService.class);

    static final String CONTENT_PATH = "clinical/paediatric-danger-signs.json";

    private final RuleContentLoader contentLoader;

    public DangerSignEvaluationService(RuleContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    /**
     * @param ageDays the child's age in days; without it no rule can be applied, because every
     *                danger-sign rule is age-scoped and applying the wrong one is the failure
     *                this service prevents
     * @param facts   observed findings and vitals, flat or nested
     */
    public DangerSignAssessment evaluate(Integer ageDays, Map<String, Object> facts) {
        return evaluate(CONTENT_PATH, ageDays, facts);
    }

    /**
     * Evaluate a named rule pack. Package-private so a test can exercise the engine against a small
     * fixture pack rather than only against shipped clinical content — a safety property is easier
     * to prove on three rules written to demonstrate it than on nine written to save lives.
     */
    DangerSignAssessment evaluate(String resourcePath, Integer ageDays, Map<String, Object> facts) {
        List<TabularRule> rules = contentLoader.load(resourcePath);
        if (rules.isEmpty()) {
            return DangerSignAssessment.unavailable(
                    "Danger-sign content is not loaded, so no assessment could be made.");
        }
        if (ageDays == null) {
            return DangerSignAssessment.unavailable(
                    "The child's age is not recorded. Danger signs differ by age, and applying the"
                            + " wrong set would be worse than applying none.");
        }

        Map<String, Object> workingFacts = new LinkedHashMap<>(facts == null ? Map.of() : facts);
        workingFacts.putIfAbsent("ageDays", ageDays);

        List<DangerSignAlert> alerts = new ArrayList<>();
        Set<String> notAssessed = new LinkedHashSet<>();
        List<String> contentProblems = new ArrayList<>();
        int applicable = 0;

        for (TabularRule rule : rules) {
            if (!rule.appliesToAge(ageDays)) {
                continue;
            }

            // Is this rule about this patient at all? Separate question from whether it fires.
            // FALSE means it does not apply and leaves nothing outstanding. UNKNOWN means we could
            // not tell — and then its inputs must be reported unassessed rather than the rule
            // quietly disappearing, because an unknown gestational age dropping every
            // gestation-scoped rule would produce a clean-looking assessment of a woman nobody
            // dated.
            if (rule.appliesWhen() != null && !rule.appliesWhen().isNull()) {
                PredicateEvaluator.Result gate =
                        PredicateEvaluator.evaluate(rule.appliesWhen(), workingFacts);
                if (gate.hasContentErrors()) {
                    contentProblems.addAll(gate.contentErrors());
                    notAssessed.addAll(rule.requiredInputs());
                    continue;
                }
                if (gate.outcome() == PredicateEvaluator.Outcome.FALSE) {
                    continue;
                }
                if (gate.outcome() == PredicateEvaluator.Outcome.UNKNOWN) {
                    notAssessed.addAll(gate.missingInputs());
                    notAssessed.addAll(rule.requiredInputs());
                    continue;
                }
            }

            applicable++;

            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(rule.logic(), workingFacts);
            if (result.hasContentErrors()) {
                // A defect in the rule must never be read as "the sign is absent".
                contentProblems.addAll(result.contentErrors());
                notAssessed.addAll(rule.requiredInputs());
                continue;
            }

            notAssessed.addAll(unassessedInputs(rule, workingFacts, result));

            if (result.holds()) {
                alerts.add(new DangerSignAlert(
                        rule.code(),
                        rule.name(),
                        rule.ruleType(),
                        rule.severity(),
                        rule.message(),
                        rule.explanation(),
                        rule.requiredAction(),
                        rule.monitoringInstruction(),
                        rule.referralRequired(),
                        rule.interruptive(),
                        rule.overrideAllowed(),
                        result.usedInputs(),
                        rule.sourceRefs(),
                        rule.contentVersion(),
                        rule.approvalStatus(),
                        rule.provenance()));
            }
        }

        boolean emergency = alerts.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.severity()));
        boolean referralRequired = alerts.stream().anyMatch(DangerSignAlert::referralRequired);

        if (!contentProblems.isEmpty()) {
            log.warn("Danger-sign content problems encountered during evaluation: {}", contentProblems);
        }
        log.info("paediatric.danger-signs.evaluated ageDays={} rulesApplied={} alerts={} emergency={} unassessed={}",
                ageDays, applicable, alerts.size(), emergency, notAssessed.size());

        return new DangerSignAssessment(true, emergency, referralRequired, alerts,
                List.copyOf(notAssessed), contentProblems, applicable, contentVersion(rules), null);
    }

    /**
     * Inputs the rule needed but did not get. Reported even when the rule already fired on
     * another sign, because the clinician still has not looked at those and a partial
     * assessment should not present itself as a complete one.
     */
    private List<String> unassessedInputs(TabularRule rule, Map<String, Object> facts,
                                          PredicateEvaluator.Result result) {
        List<String> missing = new ArrayList<>(result.missingInputs());
        for (String input : rule.requiredInputs()) {
            if (missing.contains(input) || result.usedInputs().contains(input)) {
                continue;
            }
            if (PredicateEvaluator.unassessed(PredicateEvaluator.lookup(facts, input))) {
                missing.add(input);
            }
        }
        return missing;
    }

    private String contentVersion(List<TabularRule> rules) {
        return rules.isEmpty() ? null : rules.get(0).contentVersion();
    }

    /**
     * @param assessed          false when nothing could be evaluated at all
     * @param emergency         at least one critical sign is present
     * @param notAssessed       signs the rules needed and did not have; the caller must show these
     * @param rulesApplied      how many age-applicable rules were evaluated
     */
    public record DangerSignAssessment(
            boolean assessed,
            boolean emergency,
            boolean referralRequired,
            List<DangerSignAlert> alerts,
            List<String> notAssessed,
            List<String> contentProblems,
            int rulesApplied,
            String contentVersion,
            String reason) {

        static DangerSignAssessment unavailable(String reason) {
            return new DangerSignAssessment(false, false, false, List.of(), List.of(), List.of(),
                    0, null, reason);
        }

        /** True when signs remain unassessed, so this result must not be read as an all-clear. */
        public boolean incomplete() {
            return assessed && !notAssessed.isEmpty();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("assessed", assessed);
            map.put("emergency", emergency);
            map.put("referral_required", referralRequired);
            map.put("incomplete", incomplete());
            map.put("alerts", alerts.stream().map(DangerSignAlert::toMap).toList());
            map.put("not_assessed", notAssessed);
            map.put("rules_applied", rulesApplied);
            map.put("content_version", contentVersion);
            if (!contentProblems.isEmpty()) {
                map.put("content_problems", contentProblems);
            }
            if (reason != null) {
                map.put("reason", reason);
            }
            return map;
        }
    }

    /**
     * @param triggeringFindings the observations that actually made this rule fire
     * @param approvalStatus     ENGINEERING_SEED means the content is not yet ratified
     */
    public record DangerSignAlert(
            String code,
            String name,
            String ruleType,
            String severity,
            String message,
            String explanation,
            String requiredAction,
            String monitoringInstruction,
            boolean referralRequired,
            boolean interruptive,
            boolean overrideAllowed,
            List<String> triggeringFindings,
            List<String> sourceRefs,
            String contentVersion,
            String approvalStatus,
            zw.gov.mohcc.impilo.clinical.rules.tabular.DakProvenance provenance) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("code", code);
            map.put("name", name);
            map.put("rule_type", ruleType);
            map.put("severity", severity);
            map.put("message", message);
            map.put("explanation", explanation);
            map.put("required_action", requiredAction);
            map.put("monitoring_instruction", monitoringInstruction);
            map.put("referral_required", referralRequired);
            map.put("interruptive", interruptive);
            map.put("override_allowed", overrideAllowed);
            map.put("triggering_findings", triggeringFindings);
            map.put("source_refs", sourceRefs);
            map.put("content_version", contentVersion);
            map.put("approval_status", approvalStatus);
            // Who says so, and is this the international guidance or our variation of it. Omitted
            // rather than emitted empty when a rule declares no provenance: a blank citation looks
            // like an answer, and the traceability guard is what reports the absence.
            if (provenance != null) {
                map.put("dak_provenance", provenance.toMap());
            }
            return map;
        }
    }
}
