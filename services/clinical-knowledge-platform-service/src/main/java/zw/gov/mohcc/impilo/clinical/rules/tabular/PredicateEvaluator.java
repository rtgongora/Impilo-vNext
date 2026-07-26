package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates a rule's condition, expressed as a structured predicate tree, against the facts
 * observed at this contact.
 *
 * <p>The vocabulary is closed. The evaluator understands a fixed set of combinators and
 * comparison operators and rejects anything else, so clinical content is data that can be
 * read, diffed and reviewed rather than a script that can do arbitrary things. That is what
 * makes a threshold change a governed content release instead of a code deployment.</p>
 *
 * <p>The critical property is how it treats what was not observed. A predicate whose input is
 * missing evaluates to {@link Outcome#UNKNOWN}, not false. A danger sign that was never
 * looked for is not the same as a danger sign that is absent, and collapsing the two produces
 * the single most dangerous output a clinical system can give: a confident all-clear built on
 * a question nobody asked. Callers receive the list of inputs that were missing so they can
 * say what could not be assessed.</p>
 *
 * <h2>Supported shapes</h2>
 * <pre>
 * {"all": [ ... ]}                         every child must hold
 * {"any": [ ... ]}                         at least one child must hold
 * {"not": { ... }}                         negation (UNKNOWN stays UNKNOWN)
 * {"input": "vitals.heartRate", "op": "gte", "value": 180}
 * {"input": "ageDays", "op": "between", "min": 0, "max": 59}
 * {"input": "feeding", "op": "in", "values": ["POOR", "NONE"]}
 * {"finding": "convulsions", "present": true}
 * {"input": "vitals.respiratoryRate", "bands": [
 *     {"ageMinDays": 0,  "ageMaxDays": 59,  "gte": 60},
 *     {"ageMinDays": 60, "ageMaxDays": 364, "gte": 50}]}
 * </pre>
 */
public final class PredicateEvaluator {

    /** Operators the evaluator will honour. Anything else is a content error. */
    private static final Set<String> OPERATORS =
            Set.of("eq", "ne", "gt", "gte", "lt", "lte", "between", "in", "not_in", "present", "absent");

    private PredicateEvaluator() {
    }

    /** Three-valued result: a predicate can hold, not hold, or be unanswerable. */
    public enum Outcome {
        TRUE,
        FALSE,
        UNKNOWN;

        public boolean holds() {
            return this == TRUE;
        }
    }

    /**
     * @param facts flat or dotted-path facts observed at this contact
     */
    public static Result evaluate(JsonNode predicate, Map<String, Object> facts) {
        Result result = new Result();
        result.outcome = evaluateNode(predicate, facts, result);
        return result;
    }

    private static Outcome evaluateNode(JsonNode node, Map<String, Object> facts, Result result) {
        if (node == null || node.isNull() || !node.isObject()) {
            result.contentErrors.add("A predicate must be an object");
            return Outcome.UNKNOWN;
        }

        if (node.has("all")) {
            return combineAll(node.get("all"), facts, result);
        }
        if (node.has("any")) {
            return combineAny(node.get("any"), facts, result);
        }
        if (node.has("not")) {
            Outcome inner = evaluateNode(node.get("not"), facts, result);
            return switch (inner) {
                case TRUE -> Outcome.FALSE;
                case FALSE -> Outcome.TRUE;
                case UNKNOWN -> Outcome.UNKNOWN;
            };
        }
        if (node.has("finding")) {
            return evaluateFinding(node, facts, result);
        }
        if (node.has("input")) {
            return evaluateInput(node, facts, result);
        }

        result.contentErrors.add("Unrecognised predicate shape: " + fieldNames(node));
        return Outcome.UNKNOWN;
    }

    /**
     * Every child must hold. A single false is decisive; otherwise any unknown makes the whole
     * conjunction unknown, because the remaining question could still change the answer.
     */
    private static Outcome combineAll(JsonNode children, Map<String, Object> facts, Result result) {
        if (!children.isArray() || children.isEmpty()) {
            result.contentErrors.add("'all' needs a non-empty array");
            return Outcome.UNKNOWN;
        }
        boolean anyUnknown = false;
        for (JsonNode child : children) {
            Outcome outcome = evaluateNode(child, facts, result);
            if (outcome == Outcome.FALSE) {
                return Outcome.FALSE;
            }
            if (outcome == Outcome.UNKNOWN) {
                anyUnknown = true;
            }
        }
        return anyUnknown ? Outcome.UNKNOWN : Outcome.TRUE;
    }

    /** At least one child must hold. A single true is decisive even if others are unknown. */
    private static Outcome combineAny(JsonNode children, Map<String, Object> facts, Result result) {
        if (!children.isArray() || children.isEmpty()) {
            result.contentErrors.add("'any' needs a non-empty array");
            return Outcome.UNKNOWN;
        }
        boolean anyUnknown = false;
        for (JsonNode child : children) {
            Outcome outcome = evaluateNode(child, facts, result);
            if (outcome == Outcome.TRUE) {
                return Outcome.TRUE;
            }
            if (outcome == Outcome.UNKNOWN) {
                anyUnknown = true;
            }
        }
        return anyUnknown ? Outcome.UNKNOWN : Outcome.FALSE;
    }

    /**
     * A coded clinical finding. Findings are three-state by nature: present, absent, or never
     * assessed — which is why a finding that is simply not in the map is unknown rather than
     * absent.
     */
    private static Outcome evaluateFinding(JsonNode node, Map<String, Object> facts, Result result) {
        String name = node.get("finding").asText();
        boolean expectedPresent = !node.has("present") || node.get("present").asBoolean(true);

        Object value = lookup(facts, name);
        if (value == null) {
            result.missingInputs.add(name);
            return Outcome.UNKNOWN;
        }
        Boolean present = asPresence(value);
        if (present == null) {
            result.missingInputs.add(name);
            return Outcome.UNKNOWN;
        }
        result.usedInputs.add(name);
        return present == expectedPresent ? Outcome.TRUE : Outcome.FALSE;
    }

    private static Outcome evaluateInput(JsonNode node, Map<String, Object> facts, Result result) {
        String input = node.get("input").asText();
        Object value = lookup(facts, input);

        if (node.has("bands")) {
            return evaluateBands(node, input, value, facts, result);
        }

        String op = node.path("op").asText("").toLowerCase(Locale.ROOT);
        if (!OPERATORS.contains(op)) {
            result.contentErrors.add("Unsupported operator '" + op + "' on input " + input);
            return Outcome.UNKNOWN;
        }

        if (op.equals("present") || op.equals("absent")) {
            boolean isPresent = value != null;
            result.usedInputs.add(input);
            return (op.equals("present") == isPresent) ? Outcome.TRUE : Outcome.FALSE;
        }

        if (value == null) {
            result.missingInputs.add(input);
            return Outcome.UNKNOWN;
        }
        result.usedInputs.add(input);

        return switch (op) {
            case "in" -> containsValue(node.path("values"), value) ? Outcome.TRUE : Outcome.FALSE;
            case "not_in" -> containsValue(node.path("values"), value) ? Outcome.FALSE : Outcome.TRUE;
            case "eq" -> equalsValue(node.path("value"), value) ? Outcome.TRUE : Outcome.FALSE;
            case "ne" -> equalsValue(node.path("value"), value) ? Outcome.FALSE : Outcome.TRUE;
            default -> compareNumeric(op, node, input, value, result);
        };
    }

    private static Outcome compareNumeric(String op, JsonNode node, String input, Object value, Result result) {
        Double observed = asNumber(value);
        if (observed == null) {
            // A numeric comparison against a non-numeric value is a content or data problem,
            // not a negative finding.
            result.missingInputs.add(input);
            return Outcome.UNKNOWN;
        }
        return switch (op) {
            case "gt" -> bool(observed > required(node, "value", result, input));
            case "gte" -> bool(observed >= required(node, "value", result, input));
            case "lt" -> bool(observed < required(node, "value", result, input));
            case "lte" -> bool(observed <= required(node, "value", result, input));
            case "between" -> bool(observed >= required(node, "min", result, input)
                    && observed <= required(node, "max", result, input));
            default -> Outcome.UNKNOWN;
        };
    }

    /**
     * Age-banded thresholds: the same physiological sign means different things at different
     * ages. Without an age no band applies, and the predicate is unknown rather than false.
     */
    private static Outcome evaluateBands(JsonNode node, String input, Object value,
                                         Map<String, Object> facts, Result result) {
        Double ageDays = asNumber(lookup(facts, "ageDays"));
        if (ageDays == null) {
            result.missingInputs.add("ageDays");
            return Outcome.UNKNOWN;
        }
        if (value == null) {
            result.missingInputs.add(input);
            return Outcome.UNKNOWN;
        }
        Double observed = asNumber(value);
        if (observed == null) {
            result.missingInputs.add(input);
            return Outcome.UNKNOWN;
        }
        result.usedInputs.add(input);

        for (JsonNode band : node.get("bands")) {
            double min = band.path("ageMinDays").asDouble(0);
            double max = band.has("ageMaxDays") ? band.get("ageMaxDays").asDouble() : Double.MAX_VALUE;
            if (ageDays < min || ageDays > max) {
                continue;
            }
            if (band.has("gte")) {
                return bool(observed >= band.get("gte").asDouble());
            }
            if (band.has("gt")) {
                return bool(observed > band.get("gt").asDouble());
            }
            if (band.has("lte")) {
                return bool(observed <= band.get("lte").asDouble());
            }
            if (band.has("lt")) {
                return bool(observed < band.get("lt").asDouble());
            }
            result.contentErrors.add("Band for input " + input + " has no comparison");
            return Outcome.UNKNOWN;
        }
        // No band covers this age: the content does not speak to this patient. Saying "false"
        // would assert the sign is absent, which the content never claimed.
        result.contentErrors.add("No age band covers " + ageDays.intValue() + " days for input " + input);
        return Outcome.UNKNOWN;
    }

    private static double required(JsonNode node, String field, Result result, String input) {
        if (!node.hasNonNull(field)) {
            result.contentErrors.add("Predicate on " + input + " is missing '" + field + "'");
            return Double.NaN;
        }
        return node.get(field).asDouble();
    }

    private static Outcome bool(boolean value) {
        return value ? Outcome.TRUE : Outcome.FALSE;
    }

    /** Supports dotted paths into nested maps, e.g. {@code vitals.heartRate}. */
    public static Object lookup(Map<String, Object> facts, String path) {
        if (facts == null || path == null || path.isBlank()) {
            return null;
        }
        if (facts.containsKey(path)) {
            return facts.get(path);
        }
        String[] segments = path.split("\\.");
        Object current = facts;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Reads a value as presence. Recognises booleans, the usual yes/no words, and the explicit
     * clinical states — including NOT_ASSESSED and UNKNOWN, which map to null so they are
     * treated as unanswered rather than negative.
     */
    public static Boolean asPresence(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (text) {
            case "TRUE", "YES", "PRESENT", "POSITIVE", "ABNORMAL", "Y" -> Boolean.TRUE;
            case "FALSE", "NO", "ABSENT", "NEGATIVE", "NORMAL", "N" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * True when a value means "this was never assessed" rather than carrying an answer.
     * Coded answers such as ALERT or PASSED are answers; the explicit not-assessed sentinels
     * and a blank are not.
     */
    public static boolean unassessed(Object value) {
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return true;
        }
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "NOT_ASSESSED", "NOT ASSESSED", "UNKNOWN", "NULL", "NOT_EXAMINED", "DEFERRED" -> true;
            default -> false;
        };
    }

    public static Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean equalsValue(JsonNode expected, Object actual) {
        if (expected.isNumber()) {
            Double number = asNumber(actual);
            return number != null && number == expected.asDouble();
        }
        if (expected.isBoolean()) {
            Boolean presence = asPresence(actual);
            return presence != null && presence == expected.asBoolean();
        }
        return expected.asText().equalsIgnoreCase(String.valueOf(actual).trim());
    }

    private static boolean containsValue(JsonNode values, Object actual) {
        if (!values.isArray()) {
            return false;
        }
        for (JsonNode candidate : values) {
            if (equalsValue(candidate, actual)) {
                return true;
            }
        }
        return false;
    }

    private static String fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return String.join(", ", names);
    }

    /**
     * The outcome plus what it rested on.
     *
     * @param missingInputs inputs the rule needed and did not have; the caller must be able to
     *                      say what could not be assessed rather than implying it was normal
     * @param contentErrors defects in the rule content itself, which must never be silently
     *                      treated as a negative clinical finding
     */
    public static final class Result {
        private Outcome outcome = Outcome.UNKNOWN;
        private final Set<String> missingInputs = new LinkedHashSet<>();
        private final Set<String> usedInputs = new LinkedHashSet<>();
        private final List<String> contentErrors = new ArrayList<>();

        public Outcome outcome() {
            return outcome;
        }

        public boolean holds() {
            return outcome == Outcome.TRUE;
        }

        public List<String> missingInputs() {
            return List.copyOf(missingInputs);
        }

        public List<String> usedInputs() {
            return List.copyOf(usedInputs);
        }

        public List<String> contentErrors() {
            return List.copyOf(contentErrors);
        }

        public boolean hasContentErrors() {
            return !contentErrors.isEmpty();
        }
    }
}
