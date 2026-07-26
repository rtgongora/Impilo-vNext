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

    /**
     * Evaluates a node, applying the optional-criterion waiver if the content declares one.
     *
     * <p>{@code "optionalCriterion"} is a deliberate, narrow exception to this evaluator's central
     * rule that an unanswered question is unknown rather than negative. It exists because some
     * clinical criteria are genuinely unavailable at some levels of care: weight-for-height needs a
     * height board, and most rural clinics assess acute malnutrition on mid-upper-arm circumference
     * and oedema alone, which WHO endorses. Without the waiver every nutrition classification at
     * such a clinic would be flagged provisional, and a safety flag that fires on every child is
     * worse than no flag — it teaches people to dismiss the one that matters.</p>
     *
     * <p>The waiver never hides the gap. A waived branch is recorded in {@link Result#waivedCriteria()}
     * so the classification can state which criterion was unavailable. It must only ever be used on
     * a criterion that is an <em>additional</em> route to a finding, never on one that is the sole
     * way to exclude it.</p>
     */
    private static Outcome evaluateNode(JsonNode node, Map<String, Object> facts, Result result) {
        if (node == null || node.isNull() || !node.isObject()) {
            result.contentErrors.add("A predicate must be an object");
            return Outcome.UNKNOWN;
        }

        if (node.path("optionalCriterion").asBoolean(false)) {
            Outcome inner = evaluateShape(node, facts, result);
            if (inner == Outcome.UNKNOWN) {
                result.waivedCriteria.add(describeCriterion(node));
                return Outcome.FALSE;
            }
            return inner;
        }
        return evaluateShape(node, facts, result);
    }

    private static String describeCriterion(JsonNode node) {
        if (node.has("input")) {
            return node.get("input").asText();
        }
        if (node.has("finding")) {
            return node.get("finding").asText();
        }
        return "an optional criterion";
    }

    private static Outcome evaluateShape(JsonNode node, Map<String, Object> facts, Result result) {
        if (node.has("all")) {
            return combineAll(node.get("all"), facts, result);
        }
        if (node.has("any")) {
            return combineAny(node.get("any"), facts, result);
        }
        if (node.has("atLeast")) {
            return combineAtLeast(node, facts, result);
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
     * At least N of the children must hold — IMNCI's "two of the following signs", which is how
     * the dehydration tables are written and cannot be expressed with all/any without enumerating
     * every combination.
     *
     * <p>The three-valued arithmetic is the point. With a threshold of two: two trues is decisive
     * regardless of what else is unknown; but one true and one unknown is <em>not</em> false,
     * because answering the outstanding question could complete the pair. Only when the trues plus
     * the still-unanswered cannot reach the threshold is the answer false. Treating unknowns as
     * absent here would classify a child with sunken eyes and an unexamined skin pinch as having
     * no dehydration.</p>
     */
    private static Outcome combineAtLeast(JsonNode node, Map<String, Object> facts, Result result) {
        JsonNode children = node.get("of");
        if (children == null || !children.isArray() || children.isEmpty()) {
            result.contentErrors.add("'atLeast' needs a non-empty 'of' array");
            return Outcome.UNKNOWN;
        }
        JsonNode thresholdNode = node.get("atLeast");
        if (thresholdNode == null || !thresholdNode.isNumber()) {
            result.contentErrors.add("'atLeast' must be a number");
            return Outcome.UNKNOWN;
        }
        int threshold = thresholdNode.asInt();
        if (threshold <= 0) {
            result.contentErrors.add("'atLeast' must be greater than zero");
            return Outcome.UNKNOWN;
        }

        int trues = 0;
        int unknowns = 0;
        for (JsonNode child : children) {
            switch (evaluateNode(child, facts, result)) {
                case TRUE -> trues++;
                case UNKNOWN -> unknowns++;
                default -> { /* FALSE contributes nothing and cannot later become true. */ }
            }
        }
        if (trues >= threshold) {
            return Outcome.TRUE;
        }
        return trues + unknowns >= threshold ? Outcome.UNKNOWN : Outcome.FALSE;
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
     * Banded thresholds: the same physiological sign means different things at different points on
     * some scale. Without a value for that scale no band applies, and the predicate is unknown
     * rather than false.
     *
     * <p>The scale defaults to {@code ageDays} — the paediatric case, where the same heart rate is
     * unremarkable in a neonate and alarming in a ten-year-old. Content may name a different one
     * with {@code bandKey}: gestational age, or hours since birth.
     *
     * <p><b>Why a second key rather than reusing {@code ageDays}.</b> The obvious shortcut is to
     * pass gestational age in as {@code ageDays} for maternal rules. It must never be done. Facts
     * are a flat map shared by every rule evaluated in a request, so a fact named {@code ageDays}
     * holding 196 would read as a six-month-old to every paediatric rule that saw it — including
     * the dosing engine. Two scales that are both "days" and mean entirely different things need
     * two names.
     */
    private static Outcome evaluateBands(JsonNode node, String input, Object value,
                                         Map<String, Object> facts, Result result) {
        String bandKey = node.path("bandKey").asText("ageDays");
        Double bandValue = asNumber(lookup(facts, bandKey));
        if (bandValue == null) {
            result.missingInputs.add(bandKey);
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
            // ageMinDays/ageMaxDays are the original spelling and stay accepted, so no existing
            // content changes. bandMin/bandMax read correctly when the scale is not an age.
            double min = band.has("bandMin") ? band.get("bandMin").asDouble()
                    : band.path("ageMinDays").asDouble(0);
            double max = band.has("bandMax") ? band.get("bandMax").asDouble()
                    : band.has("ageMaxDays") ? band.get("ageMaxDays").asDouble() : Double.MAX_VALUE;
            if (bandValue < min || bandValue > max) {
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
        // No band covers this patient: the content does not speak to them. Saying "false" would
        // assert the sign is absent, which the content never claimed. The message names the scale,
        // because "no band covers 196" is unreadable without knowing whether that is an age or a
        // gestation.
        result.contentErrors.add("No band covers " + bandValue.intValue() + " " + bandKey
                + " for input " + input);
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
        private final Set<String> waivedCriteria = new LinkedHashSet<>();

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

        /**
         * Criteria the content declared optional that could not be evaluated, so were treated as
         * not met. Surfaced so a classification can state what was unavailable rather than
         * implying it was assessed and negative.
         */
        public List<String> waivedCriteria() {
            return List.copyOf(waivedCriteria);
        }

        public boolean hasContentErrors() {
            return !contentErrors.isEmpty();
        }
    }
}
