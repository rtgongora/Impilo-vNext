package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contraceptive initiation practice: may she start today, and does she need backup contraception.
 *
 * <p>This is the question the eligibility matrix does not answer.
 * {@link MedicalEligibilityEngine} decides whether a woman <em>may</em> use a method. It has nothing
 * to say about when she may start it, whether she is protected on the way home, or for how long she
 * is not — and those are the questions asked at the counter. A woman handed a correct method on the
 * wrong day of her cycle, with no backup advice, has been told she is protected when she is not.
 *
 * <p><b>The checklist is evaluated here rather than as rules, because it is not a rule list.</b>
 * Reasonable certainty is a gate ANDed with a six-way alternative, resolved to one of three states.
 * Written as independent rules, "certain" and "not certain" would be logical complements and
 * <em>both</em> would stay silent whenever an input was missing — which is exactly when the answer
 * matters. The combination is therefore in code, where it is total, and the criteria stay in content,
 * where a clinician can read and revise them.
 *
 * <p><b>Nothing here refuses a woman a method.</b> The safeguard rules make that explicit, because a
 * screen that says "pregnancy cannot be excluded" and stops is read as a refusal.
 */
@Service
public class ContraceptiveInitiationService {

    private static final Logger log = LoggerFactory.getLogger(ContraceptiveInitiationService.class);

    static final String CONTENT_PATH = "clinical/rmnp-spr-rules.json";

    /** Injected into the fact map so the safeguard rules can read the resolved verdict. */
    static final String CERTAINTY_FACT = "pregnancyCertainty";

    private static final String ROLE_GATE = "EXCLUSION_GATE";
    private static final String BACKUP_RULE_TYPE = "BACKUP_CONTRACEPTION";

    private final RuleContentLoader contentLoader;
    private final ObjectMapper objectMapper;

    public ContraceptiveInitiationService(RuleContentLoader contentLoader, ObjectMapper objectMapper) {
        this.contentLoader = contentLoader;
        this.objectMapper = objectMapper;
    }

    /** One criterion of the reasonable-certainty checklist, as loaded from content. */
    record Criterion(String code, String name, String role, JsonNode logic,
                     List<String> requiredInputs, List<String> sourceRefs) {

        boolean isGate() {
            return ROLE_GATE.equals(role);
        }
    }

    /** A single piece of guidance produced for this initiation. */
    public record Guidance(
            String code,
            String name,
            String ruleType,
            String severity,
            String message,
            String explanation,
            String requiredAction,
            String monitoringInstruction,
            List<String> sourceRefs,
            Map<String, Object> provenance) {
    }

    public record CertaintyAssessment(
            PregnancyCertainty verdict,
            List<String> criteriaMet,
            List<String> criteriaUnanswered,
            List<String> missingInputs,
            String rationale) {
    }

    public record InitiationAssessment(
            CertaintyAssessment pregnancyCertainty,
            List<Guidance> guidance,
            List<String> contentProblems,
            String contentVersion,
            String approvalStatus,
            boolean complete,
            String note) {
    }

    public InitiationAssessment assess(String methodChosen, Map<String, Object> facts) {
        return assess(CONTENT_PATH, methodChosen, facts);
    }

    /**
     * Evaluate against a named pack. Package-private so a test can prove a safety property on a
     * fixture pack written to demonstrate it, rather than only on the shipped content.
     *
     * @param methodChosen the method she is starting; may be null when only the certainty question
     *                     is being asked, in which case no backup guidance is expected and the
     *                     assessment says so rather than appearing complete
     */
    InitiationAssessment assess(String resourcePath, String methodChosen, Map<String, Object> facts) {
        List<TabularRule> rules = contentLoader.load(resourcePath);
        List<Criterion> criteria = loadCriteria(resourcePath);

        if (rules.isEmpty() || criteria.isEmpty()) {
            return new InitiationAssessment(
                    new CertaintyAssessment(PregnancyCertainty.CANNOT_DETERMINE,
                            List.of(), List.of(), List.of(),
                            "The contraceptive initiation content is not loaded."),
                    List.of(),
                    List.of("Contraceptive initiation content is not loaded from " + resourcePath),
                    null, null, false,
                    "No initiation guidance could be produced. This is not a statement that none "
                            + "is needed.");
        }

        Map<String, Object> workingFacts = new LinkedHashMap<>(facts == null ? Map.of() : facts);
        if (methodChosen != null && !methodChosen.isBlank()) {
            workingFacts.put("methodChosen", methodChosen);
        }

        List<String> contentProblems = new ArrayList<>();
        CertaintyAssessment certainty = resolveCertainty(criteria, workingFacts, contentProblems);

        // The safeguard rules read the verdict rather than re-deriving it, so there is exactly one
        // place where the checklist is combined and exactly one answer on the screen.
        workingFacts.put(CERTAINTY_FACT, certainty.verdict().name());

        List<Guidance> guidance = new ArrayList<>();
        boolean backupAnswered = false;
        for (TabularRule rule : rules) {
            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(rule.logic(), workingFacts);
            if (result.hasContentErrors()) {
                contentProblems.addAll(result.contentErrors());
                continue;
            }
            if (result.outcome() != PredicateEvaluator.Outcome.TRUE) {
                continue;
            }
            if (BACKUP_RULE_TYPE.equals(rule.ruleType())) {
                backupAnswered = true;
            }
            guidance.add(new Guidance(
                    rule.code(), rule.name(), rule.ruleType(), rule.severity(),
                    rule.message(), rule.explanation(), rule.requiredAction(),
                    rule.monitoringInstruction(), rule.sourceRefs(),
                    rule.provenance() == null ? Map.of() : rule.provenance().toMap()));
        }

        // A chosen method with no backup verdict is a hole in the content, and a hole that renders
        // as an empty panel is indistinguishable from "no backup needed". The content is written so
        // every method family has an unknown-timing branch precisely so this cannot happen; if it
        // happens anyway the answer is a stated problem, never silence.
        if (methodChosen != null && !methodChosen.isBlank() && !backupAnswered) {
            contentProblems.add("No backup-contraception guidance exists for " + methodChosen
                    + ". Treat as requiring backup until the content covers it.");
        }

        boolean complete = contentProblems.isEmpty()
                && methodChosen != null && !methodChosen.isBlank()
                && certainty.verdict() != PregnancyCertainty.CANNOT_DETERMINE;

        return new InitiationAssessment(
                certainty,
                List.copyOf(guidance),
                List.copyOf(contentProblems),
                rules.get(0).contentVersion(),
                rules.get(0).approvalStatus(),
                complete,
                note(methodChosen, certainty, contentProblems));
    }

    /**
     * The SPR combination rule: no signs of pregnancy, AND any one of the criteria.
     *
     * <p>Three-valued throughout. An unmet criterion and an unasked one are not the same thing, and
     * the difference decides whether the answer is {@link PregnancyCertainty#NOT_ESTABLISHED} — ask
     * nothing more, act on it — or {@link PregnancyCertainty#CANNOT_DETERMINE}, which names a
     * question worth asking.
     */
    private CertaintyAssessment resolveCertainty(List<Criterion> criteria, Map<String, Object> facts,
                                                 List<String> contentProblems) {
        Criterion gate = criteria.stream().filter(Criterion::isGate).findFirst().orElse(null);
        List<Criterion> alternatives = criteria.stream().filter(c -> !c.isGate()).toList();

        if (gate == null) {
            contentProblems.add("The reasonable-certainty checklist declares no exclusion gate, so "
                    + "signs of pregnancy would not be checked at all.");
            return new CertaintyAssessment(PregnancyCertainty.CANNOT_DETERMINE,
                    List.of(), List.of(), List.of(),
                    "The checklist content is incomplete, so certainty could not be assessed.");
        }

        Set<String> missingInputs = new LinkedHashSet<>();
        PredicateEvaluator.Result gateResult = PredicateEvaluator.evaluate(gate.logic(), facts);
        if (gateResult.hasContentErrors()) {
            contentProblems.addAll(gateResult.contentErrors());
        }
        switch (gateResult.outcome()) {
            case FALSE -> {
                // She has symptoms or signs of pregnancy. That is an answer, and no timing criterion
                // overrules it.
                return new CertaintyAssessment(PregnancyCertainty.NOT_ESTABLISHED,
                        List.of(), List.of(), List.of(),
                        "She has symptoms or signs of pregnancy, so reasonable certainty cannot be "
                                + "established whatever the cycle timing.");
            }
            case UNKNOWN -> {
                missingInputs.addAll(gateResult.missingInputs().isEmpty()
                        ? gate.requiredInputs() : gateResult.missingInputs());
                return new CertaintyAssessment(PregnancyCertainty.CANNOT_DETERMINE,
                        List.of(), List.of(gate.code()), List.copyOf(missingInputs),
                        "It is not recorded whether she has symptoms or signs of pregnancy. The "
                                + "checklist cannot be applied until that is asked.");
            }
            case TRUE -> { /* no signs; the alternatives decide */ }
        }

        List<String> met = new ArrayList<>();
        List<String> unanswered = new ArrayList<>();
        for (Criterion criterion : alternatives) {
            PredicateEvaluator.Result result = PredicateEvaluator.evaluate(criterion.logic(), facts);
            if (result.hasContentErrors()) {
                contentProblems.addAll(result.contentErrors());
                unanswered.add(criterion.code());
                missingInputs.addAll(criterion.requiredInputs());
                continue;
            }
            switch (result.outcome()) {
                case TRUE -> met.add(criterion.code());
                case UNKNOWN -> {
                    unanswered.add(criterion.code());
                    missingInputs.addAll(result.missingInputs().isEmpty()
                            ? criterion.requiredInputs() : result.missingInputs());
                }
                case FALSE -> { /* asked, and does not apply */ }
            }
        }

        if (!met.isEmpty()) {
            // One met criterion is sufficient. The remaining unanswered ones cannot change the
            // answer, so they are not reported — a result flagged provisional for questions that
            // could not have altered it trains people to ignore the flag.
            return new CertaintyAssessment(PregnancyCertainty.ESTABLISHED,
                    List.copyOf(met), List.of(), List.of(),
                    "Reasonably certain she is not pregnant: no signs of pregnancy, and "
                            + met.get(0) + " is met.");
        }
        if (!unanswered.isEmpty()) {
            return new CertaintyAssessment(PregnancyCertainty.CANNOT_DETERMINE,
                    List.of(), List.copyOf(unanswered), List.copyOf(missingInputs),
                    "No criterion is met, but " + unanswered.size() + " of the checklist was not "
                            + "asked. Any one of them would settle it: " + String.join(", ",
                            missingInputs) + ".");
        }
        return new CertaintyAssessment(PregnancyCertainty.NOT_ESTABLISHED,
                List.of(), List.of(), List.of(),
                "Every criterion in the checklist was asked and none is met, so pregnancy cannot be "
                        + "excluded on history alone.");
    }

    private List<Criterion> loadCriteria(String resourcePath) {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.error("Contraceptive initiation content not found on the classpath: {}", resourcePath);
                return List.of();
            }
            JsonNode root = objectMapper.readTree(stream);
            List<Criterion> criteria = new ArrayList<>();
            for (JsonNode node : root.path("criteria")) {
                JsonNode logic = node.get("logic");
                String code = node.path("code").asText(null);
                if (code == null || logic == null || logic.isNull()) {
                    // Same fail-closed stance as the rule loader: a half-understood criterion that
                    // silently evaluates to nothing would weaken the checklist without saying so.
                    log.warn("Rejecting reasonable-certainty criterion without a code or logic: {}", code);
                    continue;
                }
                criteria.add(new Criterion(code, node.path("name").asText(null),
                        node.path("role").asText("ANY_ONE_OF"), logic,
                        strings(node.path("requiredInputs")), strings(node.path("sourceRefs"))));
            }
            return List.copyOf(criteria);
        } catch (IOException e) {
            log.error("Failed to read contraceptive initiation content {}: {}", resourcePath,
                    e.getMessage(), e);
            return List.of();
        }
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.asText()));
        return List.copyOf(out);
    }

    private static String note(String methodChosen, CertaintyAssessment certainty,
                               List<String> contentProblems) {
        if (!contentProblems.isEmpty()) {
            return "Some initiation guidance could not be produced. Treat the gaps as unanswered, "
                    + "not as reassurance.";
        }
        if (methodChosen == null || methodChosen.isBlank()) {
            return "No method was named, so this covers the pregnancy-certainty question only. "
                    + "Backup contraception has not been assessed.";
        }
        if (certainty.verdict() == PregnancyCertainty.CANNOT_DETERMINE) {
            return "Backup guidance below is complete. The pregnancy question is not, and the "
                    + "unanswered criteria are listed.";
        }
        return "Initiation assessed against the recorded history and the method chosen.";
    }
}
