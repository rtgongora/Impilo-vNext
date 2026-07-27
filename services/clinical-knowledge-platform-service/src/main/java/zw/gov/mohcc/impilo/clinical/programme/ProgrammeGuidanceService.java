package zw.gov.mohcc.impilo.clinical.programme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.tabular.DakProvenance;
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
 * Governed decision support for the HIV and TB programmes — viral-load interpretation and treatment
 * failure for HIV; phase transition, bacteriological monitoring, resistance and outcome for TB.
 *
 * <p>Modelled on {@link zw.gov.mohcc.impilo.clinical.danger.DangerSignEvaluationService}, but scoped
 * by programme rather than by age. The same safety property is central and enforced the same way:
 * <strong>silence is never reassurance.</strong> A rule whose inputs were not supplied is reported
 * as unassessed and the result is marked incomplete, because "no failure detected" built on a viral
 * load nobody drew is the most dangerous output this service could give — it reads as an all-clear.</p>
 *
 * <p>The engine is shared content-driven infrastructure: it extends only through the pack JSON and
 * the {@code appliesWhen} / {@code logic} hooks, and never edits {@code PredicateEvaluator}, which is
 * shared by four engines.</p>
 */
@Service
public class ProgrammeGuidanceService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeGuidanceService.class);

    static final String HIV_CONTENT_PATH = "clinical/hiv-programme-rules.json";
    static final String TB_CONTENT_PATH = "clinical/tb-programme-rules.json";
    static final String HIV_TESTING_PATH = "clinical/hiv-testing-rules.json";
    static final String TB_SCREENING_PATH = "clinical/tb-screening-rules.json";
    static final String TB_DIAGNOSIS_PATH = "clinical/tb-diagnosis-rules.json";
    static final String TPT_PATH = "clinical/tpt-rules.json";
    static final String PREP_PATH = "clinical/prep-rules.json";
    static final String PMTCT_PATH = "clinical/pmtct-rules.json";
    // W4 chronic-disease CDS — the same tabular engine, evaluated against PatientFacts
    // (egfr/weightKg/hepaticImpairment) plus caller facts. Not programmes; content topics.
    static final String CVD_RISK_PATH = "clinical/cvd-risk-rules.json";
    static final String DEPRESCRIBING_PATH = "clinical/deprescribing-rules.json";
    // W5 medical-procedure indication + W6 specialty CDS (ICOPE, mhGAP, AMS, palliative, oncology).
    static final String PROCEDURE_INDICATION_PATH = "clinical/procedure-indication-rules.json";
    static final String ICOPE_PATH = "clinical/icope-rules.json";
    static final String MHGAP_PATH = "clinical/mhgap-rules.json";
    static final String AMS_PATH = "clinical/antimicrobial-stewardship-rules.json";
    static final String PALLIATIVE_PATH = "clinical/palliative-rules.json";
    static final String ONCOLOGY_PATH = "clinical/oncology-early-diagnosis-rules.json";

    private final RuleContentLoader contentLoader;

    public ProgrammeGuidanceService(RuleContentLoader contentLoader) {
        this.contentLoader = contentLoader;
    }

    /** Resolves the pack for a programme, or null if the programme has no governed content. */
    static String contentPathFor(String programme) {
        if (programme == null) {
            return null;
        }
        // Topics arrive kebab-case from the URL (procedure-indication) or snake from callers;
        // normalise both to the underscore form the cases use.
        return switch (programme.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')) {
            case "HIV_CARE", "HIV" -> HIV_CONTENT_PATH;
            case "TB_TREATMENT", "TB" -> TB_CONTENT_PATH;
            case "HIV_TESTING" -> HIV_TESTING_PATH;
            case "TB_SCREENING" -> TB_SCREENING_PATH;
            case "TB_DIAGNOSIS" -> TB_DIAGNOSIS_PATH;
            case "TB_PREVENTION", "TPT" -> TPT_PATH;
            case "HIV_PREVENTION", "PREP" -> PREP_PATH;
            case "PMTCT" -> PMTCT_PATH;
            case "CVD_RISK", "CARDIOVASCULAR_RISK" -> CVD_RISK_PATH;
            case "DEPRESCRIBING", "MEDICATION_REVIEW" -> DEPRESCRIBING_PATH;
            case "PROCEDURE_INDICATION", "PROCEDURES" -> PROCEDURE_INDICATION_PATH;
            case "ICOPE", "GERIATRIC", "GERIATRICS" -> ICOPE_PATH;
            case "MHGAP", "MENTAL_HEALTH" -> MHGAP_PATH;
            case "ANTIMICROBIAL_STEWARDSHIP", "AMS" -> AMS_PATH;
            case "PALLIATIVE", "PALLIATIVE_CARE" -> PALLIATIVE_PATH;
            case "ONCOLOGY", "CANCER" -> ONCOLOGY_PATH;
            default -> null;
        };
    }

    public ProgrammeAssessment evaluate(String programme, Map<String, Object> facts) {
        String path = contentPathFor(programme);
        if (path == null) {
            return ProgrammeAssessment.unavailable(
                    "No governed content for programme '" + programme + "'. HIV_CARE and TB_TREATMENT "
                    + "are supported; nothing is evaluated rather than guessing.");
        }
        return evaluatePack(path, facts);
    }

    /**
     * Evaluate a named pack. Package-private so a test can drive the real engine against the shipped
     * content and its fixtures — the governance gate for this pack.
     */
    ProgrammeAssessment evaluatePack(String resourcePath, Map<String, Object> facts) {
        List<TabularRule> rules = contentLoader.load(resourcePath);
        if (rules.isEmpty()) {
            return ProgrammeAssessment.unavailable(
                    "Programme content is not loaded, so no guidance could be produced.");
        }

        Map<String, Object> workingFacts = new LinkedHashMap<>(facts == null ? Map.of() : facts);

        List<ProgrammeAlert> alerts = new ArrayList<>();
        Set<String> notAssessed = new LinkedHashSet<>();
        List<String> contentProblems = new ArrayList<>();
        int applicable = 0;

        for (TabularRule rule : rules) {
            // Is this rule about this patient at all? A phase-transition rule does not "fail to fire"
            // for a patient already in the continuation phase — it does not apply. UNKNOWN means we
            // could not tell, and then the inputs are reported unassessed rather than the rule
            // quietly vanishing.
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
                // A defect in the rule must never read as "no problem found".
                contentProblems.addAll(result.contentErrors());
                notAssessed.addAll(rule.requiredInputs());
                continue;
            }

            notAssessed.addAll(unassessedInputs(rule, workingFacts, result));

            if (result.holds()) {
                alerts.add(new ProgrammeAlert(
                        rule.code(), rule.name(), rule.ruleType(), rule.severity(),
                        rule.message(), rule.explanation(), rule.requiredAction(),
                        rule.monitoringInstruction(), rule.referralRequired(), rule.interruptive(),
                        rule.overrideAllowed(), result.usedInputs(), rule.sourceRefs(),
                        rule.contentVersion(), rule.approvalStatus(), rule.provenance()));
            }
        }

        boolean referralRequired = alerts.stream().anyMatch(ProgrammeAlert::referralRequired);
        boolean interruptive = alerts.stream().anyMatch(ProgrammeAlert::interruptive);

        if (!contentProblems.isEmpty()) {
            log.warn("Programme content problems during evaluation of {}: {}", resourcePath, contentProblems);
        }
        log.info("programme.guidance.evaluated pack={} rulesApplied={} alerts={} referral={} unassessed={}",
                resourcePath, applicable, alerts.size(), referralRequired, notAssessed.size());

        return new ProgrammeAssessment(true, referralRequired, interruptive, alerts,
                List.copyOf(notAssessed), contentProblems, applicable, contentVersion(rules), null);
    }

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
     * @param assessed      false when nothing could be evaluated at all
     * @param notAssessed   inputs the rules needed and did not have; the caller must show these
     * @param rulesApplied  how many applicable rules were evaluated
     */
    public record ProgrammeAssessment(
            boolean assessed,
            boolean referralRequired,
            boolean interruptive,
            List<ProgrammeAlert> alerts,
            List<String> notAssessed,
            List<String> contentProblems,
            int rulesApplied,
            String contentVersion,
            String reason) {

        static ProgrammeAssessment unavailable(String reason) {
            return new ProgrammeAssessment(false, false, false, List.of(), List.of(), List.of(),
                    0, null, reason);
        }

        /** True when inputs remain unassessed, so this result must not be read as an all-clear. */
        public boolean incomplete() {
            return assessed && !notAssessed.isEmpty();
        }

        public boolean fired(String code) {
            return alerts.stream().anyMatch(a -> a.code().equals(code));
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("assessed", assessed);
            map.put("referral_required", referralRequired);
            map.put("interruptive", interruptive);
            map.put("incomplete", incomplete());
            map.put("alerts", alerts.stream().map(ProgrammeAlert::toMap).toList());
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

    public record ProgrammeAlert(
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
            DakProvenance provenance) {

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
            if (provenance != null) {
                map.put("dak_provenance", provenance.toMap());
            }
            return map;
        }
    }
}
