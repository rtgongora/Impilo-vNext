package zw.gov.mohcc.impilo.emergency.triage;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import zw.gov.mohcc.impilo.emergency.triage.corpus.IittCorpusRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * JVM face of the shared IITT corpus.
 *
 * <p>The scenarios themselves live in {@code classpath:/iitt-corpus/scenarios.json}. The TeaVM
 * browser harness runs the same file through {@link IittCorpusRunner}. Keeping the assertions here
 * thin — one dynamic test per scenario id — means a failure names the scenario, not a bulk dump.
 */
class IittCorpusTestFactory {

    @TestFactory
    Collection<DynamicTest> corpus() {
        List<Map<String, Object>> scenarios = IittCorpusRunner.scenarios();
        List<DynamicTest> tests = new ArrayList<>(scenarios.size());
        for (Map<String, Object> scenario : scenarios) {
            String id = String.valueOf(scenario.get("id"));
            String source = String.valueOf(scenario.get("source"));
            tests.add(dynamicTest(id + " ← " + source, () -> {
                String failure = IittCorpusRunner.runScenario(scenario);
                assertTrue(failure == null, () -> id + ": " + failure);
            }));
        }
        return tests;
    }
}
