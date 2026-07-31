package zw.gov.mohcc.impilo.emergency.triage.corpus;

import zw.gov.mohcc.impilo.emergency.triage.IittChart;
import zw.gov.mohcc.impilo.emergency.triage.IittCriterion;
import zw.gov.mohcc.impilo.emergency.triage.PaediatricVitalBand;
import zw.gov.mohcc.impilo.emergency.triage.TriageFacts;
import zw.gov.mohcc.impilo.emergency.triage.TriagePriority;
import zw.gov.mohcc.impilo.emergency.triage.TriageResult;
import zw.gov.mohcc.impilo.emergency.triage.WhoIittEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs the shared IITT JSON corpus against {@link WhoIittEngine}.
 *
 * <p>Used by the JVM {@code @TestFactory} and by the TeaVM browser harness so both execute the
 * same scenarios. Returning failure strings (rather than throwing assertion errors) keeps this
 * class free of a JUnit dependency and runnable under TeaVM.
 */
public final class IittCorpusRunner {

    public static final String CORPUS_RESOURCE = "/iitt-corpus/scenarios.json";

    private IittCorpusRunner() {}

    /** Run a single already-parsed scenario object; returns null on pass, failure text on fail. */
    public static String runScenario(Map<String, Object> scenario) {
        String id = String.valueOf(scenario.get("id"));
        try {
            return runOne(scenario);
        } catch (RuntimeException e) {
            return "unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    /** Load and run every scenario; returns one failure line per mismatch (empty = all passed). */
    public static List<String> runAll() {
        Map<String, Object> root = CorpusJson.asObject(CorpusJson.parse(CorpusJson.readClasspath(CORPUS_RESOURCE)));
        List<Object> scenarios = CorpusJson.asArray(root.get("scenarios"));
        List<String> failures = new ArrayList<>();
        for (Object raw : scenarios) {
            Map<String, Object> scenario = CorpusJson.asObject(raw);
            String id = String.valueOf(scenario.get("id"));
            String failure = runScenario(scenario);
            if (failure != null) {
                failures.add(id + ": " + failure);
            }
        }
        return failures;
    }

    /** Every scenario object from the shared corpus, in file order. */
    public static List<Map<String, Object>> scenarios() {
        Map<String, Object> root = CorpusJson.asObject(CorpusJson.parse(CorpusJson.readClasspath(CORPUS_RESOURCE)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object raw : CorpusJson.asArray(root.get("scenarios"))) {
            out.add(CorpusJson.asObject(raw));
        }
        return out;
    }

    public static int scenarioCount() {
        return scenarios().size();
    }

    private static String runOne(Map<String, Object> scenario) {
        String kind = String.valueOf(scenario.get("kind"));
        return switch (kind) {
            case "evaluate" -> runEvaluate(scenario);
            case "evaluateThrows" -> runEvaluateThrows(scenario);
            case "inventory" -> runInventory(scenario);
            case "property" -> runProperty(scenario);
            default -> "unknown kind " + kind;
        };
    }

    private static String runEvaluate(Map<String, Object> scenario) {
        TriageResult result = WhoIittEngine.evaluate(buildFacts(CorpusJson.asObject(scenario.get("facts"))));
        return checkExpect(result, CorpusJson.asObject(scenario.get("expect")));
    }

    private static String runEvaluateThrows(Map<String, Object> scenario) {
        Map<String, Object> expect = CorpusJson.asObject(scenario.get("expectThrows"));
        String simpleName = String.valueOf(expect.get("exceptionSimpleName"));
        String messageContains = expect.get("messageContains") == null
                ? null
                : String.valueOf(expect.get("messageContains"));
        try {
            WhoIittEngine.evaluate(buildFacts(CorpusJson.asObject(scenario.get("facts"))));
            return "expected " + simpleName + " but evaluate returned";
        } catch (RuntimeException e) {
            if (!e.getClass().getSimpleName().equals(simpleName)) {
                return "expected " + simpleName + " but got " + e.getClass().getSimpleName();
            }
            if (messageContains != null && (e.getMessage() == null || !e.getMessage().contains(messageContains))) {
                return "exception message missing '" + messageContains + "': " + e.getMessage();
            }
            return null;
        }
    }

    private static String runInventory(Map<String, Object> scenario) {
        Map<String, Object> inv = CorpusJson.asObject(scenario.get("inventory"));
        List<IittCriterion> chart = chartOf(String.valueOf(inv.get("chart")));
        Set<String> actual = chart.stream().map(IittCriterion::code).collect(Collectors.toSet());

        if (inv.get("exactCodes") != null) {
            List<Object> expected = CorpusJson.asArray(inv.get("exactCodes"));
            for (Object code : expected) {
                if (!actual.contains(String.valueOf(code))) {
                    return "criterion missing from chart: " + code;
                }
            }
            if (actual.size() != expected.size()) {
                return "criterion count changed: expected " + expected.size() + " got " + actual.size();
            }
        }
        if (inv.get("exactCount") != null) {
            int expected = ((Number) inv.get("exactCount")).intValue();
            if (chart.size() != expected) {
                return "criterion count changed: expected " + expected + " got " + chart.size();
            }
        }
        if (inv.get("codeMustNotContain") != null) {
            String needle = String.valueOf(inv.get("codeMustNotContain"));
            for (String code : actual) {
                if (code.contains(needle)) {
                    return "chart must not carry a code containing " + needle + " but has " + code;
                }
            }
        }
        return null;
    }

    private static String runProperty(Map<String, Object> scenario) {
        Map<String, Object> prop = CorpusJson.asObject(scenario.get("property"));
        if (!"paediatricBandsContiguous".equals(String.valueOf(prop.get("name")))) {
            return "unknown property " + prop.get("name");
        }
        int from = ((Number) prop.get("ageDaysFromInclusive")).intValue();
        int to = ((Number) prop.get("ageDaysToExclusive")).intValue();
        for (int day = from; day < to; day++) {
            if (PaediatricVitalBand.forAgeDays(day) == null) {
                return "no band covers day " + day;
            }
        }
        return null;
    }

    private static String checkExpect(TriageResult result, Map<String, Object> expect) {
        if (expect.get("priority") != null) {
            TriagePriority want = TriagePriority.valueOf(String.valueOf(expect.get("priority")));
            if (result.priority() != want) {
                return "priority expected " + want + " got " + result.priority();
            }
        }
        if (expect.get("priorityNot") != null) {
            TriagePriority forbidden = TriagePriority.valueOf(String.valueOf(expect.get("priorityNot")));
            if (result.priority() == forbidden) {
                return "priority must not be " + forbidden;
            }
        }
        if (expect.get("chart") != null
                && !String.valueOf(expect.get("chart")).equals(result.chart())) {
            return "chart expected " + expect.get("chart") + " got " + result.chart();
        }
        if (expect.get("requiresClinicianReview") != null) {
            boolean want = Boolean.TRUE.equals(expect.get("requiresClinicianReview"));
            if (result.requiresClinicianReview() != want) {
                return "requiresClinicianReview expected " + want;
            }
        }
        if (Boolean.TRUE.equals(expect.get("triggeringCodesEmpty"))
                && !result.triggeringCriteria().isEmpty()) {
            return "expected no triggering criteria, got "
                    + result.triggeringCriteria().stream().map(IittCriterion::code).toList();
        }
        if (expect.get("triggeringCodes") != null) {
            Set<String> actual = result.triggeringCriteria().stream()
                    .map(IittCriterion::code).collect(Collectors.toSet());
            for (Object code : CorpusJson.asArray(expect.get("triggeringCodes"))) {
                if (!actual.contains(String.valueOf(code))) {
                    return "missing triggering code " + code + " in " + actual;
                }
            }
        }
        if (expect.get("triggeringCodesForbidden") != null) {
            Set<String> actual = result.triggeringCriteria().stream()
                    .map(IittCriterion::code).collect(Collectors.toSet());
            for (Object code : CorpusJson.asArray(expect.get("triggeringCodesForbidden"))) {
                if (actual.contains(String.valueOf(code))) {
                    return "forbidden triggering code fired: " + code;
                }
            }
        }
        if (expect.get("allTriggeringTiers") != null) {
            TriagePriority want = TriagePriority.valueOf(String.valueOf(expect.get("allTriggeringTiers")));
            for (IittCriterion c : result.triggeringCriteria()) {
                if (c.tier() != want) {
                    return "triggering criterion " + c.code() + " has tier " + c.tier() + " not " + want;
                }
            }
        }
        if (expect.get("highRiskVitalPrefixes") != null) {
            for (Object prefix : CorpusJson.asArray(expect.get("highRiskVitalPrefixes"))) {
                String p = String.valueOf(prefix);
                boolean found = result.highRiskVitalSigns().stream().anyMatch(s -> s.startsWith(p));
                if (!found) {
                    return "no high-risk vital starts with '" + p + "': " + result.highRiskVitalSigns();
                }
            }
        }
        if (Boolean.TRUE.equals(expect.get("highRiskEmpty")) && !result.highRiskVitalSigns().isEmpty()) {
            return "high-risk vitals should be empty, got " + result.highRiskVitalSigns();
        }
        if (expect.get("unassessedContains") != null) {
            for (Object input : CorpusJson.asArray(expect.get("unassessedContains"))) {
                if (!result.unassessedInputs().contains(String.valueOf(input))) {
                    return "unassessedInputs missing " + input + ": " + result.unassessedInputs();
                }
            }
        }
        if (Boolean.TRUE.equals(expect.get("unassessedEmpty")) && !result.unassessedInputs().isEmpty()) {
            return "unassessedInputs should be empty, got " + result.unassessedInputs();
        }
        if (expect.get("destinationContains") != null) {
            String needle = String.valueOf(expect.get("destinationContains"));
            if (result.destination() == null || !result.destination().contains(needle)) {
                return "destination missing '" + needle + "': " + result.destination();
            }
        }
        return null;
    }

    private static TriageFacts buildFacts(Map<String, Object> facts) {
        TriageFacts.Builder b;
        String fixture = facts.get("fixture") == null ? "none" : String.valueOf(facts.get("fixture"));
        Integer ageDays = facts.get("ageDays") == null ? null : ((Number) facts.get("ageDays")).intValue();

        b = switch (fixture) {
            case "completeNormalAdult" -> completeNormalAdult();
            case "completeNormalChild" -> completeNormalChild();
            case "allSignsAbsent" -> allSignsAbsent(ageDays != null ? ageDays : 30 * 365);
            default -> TriageFacts.builder();
        };

        if (ageDays != null) {
            b.ageDays(ageDays);
        }
        if (facts.containsKey("pregnant")) {
            Object p = facts.get("pregnant");
            b.pregnant(p == null ? null : (Boolean) p);
        }
        if (facts.get("signs") != null) {
            Map<String, Object> signs = CorpusJson.asObject(facts.get("signs"));
            for (Map.Entry<String, Object> e : signs.entrySet()) {
                b.sign(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
        if (facts.get("vitals") != null) {
            Map<String, Object> vitals = CorpusJson.asObject(facts.get("vitals"));
            for (Map.Entry<String, Object> e : vitals.entrySet()) {
                b.vital(e.getKey(), ((Number) e.getValue()).doubleValue());
            }
        }
        return b.build();
    }

    private static TriageFacts.Builder completeNormalAdult() {
        return allSignsAbsent(30 * 365)
                .vital(IittChart.HR, 80).vital(IittChart.RR, 16)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0);
    }

    private static TriageFacts.Builder completeNormalChild() {
        return allSignsAbsent(3 * 365)
                .vital(IittChart.HR, 110).vital(IittChart.RR, 24)
                .vital(IittChart.TEMP, 37.0).vital(IittChart.SPO2, 98).vital(IittChart.AVPU, 0);
    }

    private static TriageFacts.Builder allSignsAbsent(int ageDays) {
        TriageFacts.Builder b = TriageFacts.builder().ageDays(ageDays).pregnant(false);
        for (IittCriterion c : IittChart.adultRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.adultYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricRed()) c.requiredSigns().forEach(s -> b.sign(s, false));
        for (IittCriterion c : IittChart.paediatricYellow()) c.requiredSigns().forEach(s -> b.sign(s, false));
        return b;
    }

    private static List<IittCriterion> chartOf(String name) {
        return switch (name) {
            case "adultRed" -> IittChart.adultRed();
            case "adultYellow" -> IittChart.adultYellow();
            case "paediatricRed" -> IittChart.paediatricRed();
            case "paediatricYellow" -> IittChart.paediatricYellow();
            default -> throw new IllegalArgumentException("unknown chart " + name);
        };
    }
}
