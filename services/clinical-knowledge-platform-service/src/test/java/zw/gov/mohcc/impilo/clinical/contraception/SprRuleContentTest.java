package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.clinical.rules.tabular.PredicateEvaluator;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;
import zw.gov.mohcc.impilo.clinical.rules.tabular.TabularRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The governance gate for the contraceptive initiation content.
 *
 * <p>Every rule ships fixtures and they run against the live evaluator, so a content edit that
 * changes clinical behaviour fails the build rather than reaching a counter. The structural
 * assertions below exist because this pack's central safety property is a property of the content
 * as a whole, not of any single rule: for each method family the timing branches must partition
 * completely, including the branch for when the timing is not known.
 */
class SprRuleContentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleContentLoader loader = new RuleContentLoader(objectMapper);
    private final ContraceptiveInitiationService service =
            new ContraceptiveInitiationService(loader, objectMapper);

    private List<TabularRule> rules() {
        return loader.load(ContraceptiveInitiationService.CONTENT_PATH);
    }

    private JsonNode pack() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(ContraceptiveInitiationService.CONTENT_PATH)) {
            return objectMapper.readTree(stream);
        }
    }

    @Test
    void thePackLoads() {
        assertThat(rules()).isNotEmpty();
    }

    @TestFactory
    List<DynamicTest> everyRulesOwnFixturesStillBehaveAsDescribed() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TabularRule rule : rules()) {
            assertThat(rule.testCases())
                    .as("rule %s ships no fixtures, so a change to it could not be caught", rule.code())
                    .isNotEmpty();

            for (TabularRule.TestCase testCase : rule.testCases()) {
                tests.add(DynamicTest.dynamicTest(rule.code() + " — " + testCase.name(), () -> {
                    var result = PredicateEvaluator.evaluate(rule.logic(), testCase.facts());
                    assertThat(result.hasContentErrors())
                            .as("%s has a content error: %s", rule.code(), result.contentErrors())
                            .isFalse();
                    assertThat(result.outcome() == PredicateEvaluator.Outcome.TRUE)
                            .as("%s: %s", rule.code(), testCase.name())
                            .isEqualTo(testCase.expectFires());
                }));
            }
        }
        return tests;
    }

    @Test
    void everyRuleStatesWhatToDoAndWhereItCameFrom() {
        for (TabularRule rule : rules()) {
            assertThat(rule.requiredAction())
                    .as("%s must tell a clinician what to do", rule.code()).isNotBlank();
            assertThat(rule.sourceRefs())
                    .as("%s must cite its source", rule.code()).isNotEmpty();
            assertThat(rule.requiredInputs())
                    .as("%s must declare the facts it reads", rule.code()).isNotEmpty();
            assertThat(rule.provenance())
                    .as("%s must carry an adaptation block", rule.code()).isNotNull();
            assertThat(rule.provenance().adaptationType())
                    .as("%s must say what was done to the source text", rule.code()).isNotBlank();
        }
    }

    @Test
    void everyCertaintyCriterionCitesItsSourceAndDeclaresItsInputs() throws IOException {
        JsonNode criteria = pack().path("criteria");
        assertThat(criteria).isNotEmpty();

        int gates = 0;
        for (JsonNode criterion : criteria) {
            String code = criterion.path("code").asText();
            assertThat(criterion.path("sourceRefs")).as("%s cites nothing", code).isNotEmpty();
            assertThat(criterion.path("requiredInputs"))
                    .as("%s declares no inputs, so an unanswered one could not be named", code)
                    .isNotEmpty();
            assertThat(criterion.path("adaptation").path("approvingAuthority").asText())
                    .as("%s names no approving authority", code).isNotBlank();
            if ("EXCLUSION_GATE".equals(criterion.path("role").asText())) {
                gates++;
            }
        }
        // Exactly one gate. Two would mean two different things could block the checklist and only
        // the first would be evaluated; none would mean signs of pregnancy were never checked.
        assertThat(gates).isEqualTo(1);
    }

    /**
     * The property that matters, stated over the content rather than over one rule.
     *
     * <p>For every method the pack speaks to, the timing branches must cover the whole space —
     * inside the window, outside it, and timing not established. A family missing the third branch
     * compiles, passes its own fixtures, and produces an empty panel for a woman whose last
     * menstrual period is unknown. That panel is read as "no backup needed".
     */
    @Test
    void everyMethodTheContentCoversHasABranchForUnknownTiming() {
        Set<String> methodsWithGuidance = new LinkedHashSet<>();
        for (TabularRule rule : rules()) {
            if ("BACKUP_CONTRACEPTION".equals(rule.ruleType())) {
                collectMethodCodes(rule.logic(), methodsWithGuidance);
            }
        }
        assertThat(methodsWithGuidance).isNotEmpty();

        for (String method : methodsWithGuidance) {
            var backup = service.assess(method, java.util.Map.of()).guidance().stream()
                    .filter(g -> "BACKUP_CONTRACEPTION".equals(g.ruleType())).toList();
            assertThat(backup)
                    .as("method %s produces no backup guidance when the cycle day is unknown; "
                            + "the panel would render empty and be read as reassurance", method)
                    .hasSize(1);
        }
    }

    @Test
    void thePackDeclaresWhetherItsPrimaryTextIsVendored() throws IOException {
        JsonNode verification = pack().path("provenance").path("sourceVerification");

        // Not an assertion that it IS vendored — it is not. An assertion that the pack says so
        // where a reader will see it, because this content was authored against published text
        // rather than extracted from a pinned artefact, and that departure must not be silent.
        assertThat(verification.has("primaryTextVendored")).isTrue();
        assertThat(verification.path("note").asText()).isNotBlank();
    }

    private static void collectMethodCodes(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if ("methodChosen".equals(node.path("input").asText())) {
                node.path("values").forEach(value -> out.add(value.asText()));
            }
            node.forEach(child -> collectMethodCodes(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collectMethodCodes(child, out));
        }
    }
}
