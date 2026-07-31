package zw.gov.mohcc.impilo.clinical.calculator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One field a calculator needs, described well enough for a generic form to render it and for a
 * clinician to know what is being asked.
 *
 * @param key      the name this value is submitted under
 * @param label    the question as it should be put to a clinician
 * @param kind     how the value is entered
 * @param unit     the unit the value must be entered in, or null when it has none. Where a
 *                 calculator accepts a value in more than one unit, each unit is a separate input
 *                 on a separate variant rather than a unit selector, because a mis-set selector is
 *                 indistinguishable from a correct one after the fact
 * @param required whether the calculator can produce anything without it. A false here does not
 *                 mean the field is unimportant — optional fields such as albumin change what the
 *                 result means, and the result says whether they were supplied
 * @param options  the permitted values for {@link InputKind#ENUM} and {@link InputKind#ENUM_MULTI};
 *                 empty otherwise
 * @param help     a sentence explaining what the field is for or how it is commonly got wrong;
 *                 null when the label is self-explanatory
 */
public record CalculatorInput(String key,
                              String label,
                              InputKind kind,
                              String unit,
                              boolean required,
                              List<CalculatorOption> options,
                              String help) {

    /** A required numeric field with a unit. */
    public static CalculatorInput decimal(String key, String label, String unit, String help) {
        return new CalculatorInput(key, label, InputKind.DECIMAL, unit, true, List.of(), help);
    }

    /** An optional numeric field with a unit. */
    public static CalculatorInput optionalDecimal(String key, String label, String unit, String help) {
        return new CalculatorInput(key, label, InputKind.DECIMAL, unit, false, List.of(), help);
    }

    /** A required whole-number field. */
    public static CalculatorInput integer(String key, String label, String unit, String help) {
        return new CalculatorInput(key, label, InputKind.INTEGER, unit, true, List.of(), help);
    }

    /** An optional whole-number field. */
    public static CalculatorInput optionalInteger(String key, String label, String unit, String help) {
        return new CalculatorInput(key, label, InputKind.INTEGER, unit, false, List.of(), help);
    }

    /** A required yes/no question that must be answered explicitly. */
    public static CalculatorInput bool(String key, String label, String help) {
        return new CalculatorInput(key, label, InputKind.BOOLEAN, null, true, List.of(), help);
    }

    /** An optional yes/no question. */
    public static CalculatorInput optionalBool(String key, String label, String help) {
        return new CalculatorInput(key, label, InputKind.BOOLEAN, null, false, List.of(), help);
    }

    /** A required single choice. */
    public static CalculatorInput choice(String key, String label, List<CalculatorOption> options,
                                         String help) {
        return new CalculatorInput(key, label, InputKind.ENUM, null, true, List.copyOf(options), help);
    }

    /** A multi-select where an empty selection is itself a meaningful answer. */
    public static CalculatorInput multiChoice(String key, String label, List<CalculatorOption> options,
                                              String help) {
        return new CalculatorInput(key, label, InputKind.ENUM_MULTI, null, true, List.copyOf(options),
                help);
    }

    /** Wire form, snake_case to match the rest of the CKP surface. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("kind", kind.name());
        m.put("unit", unit);
        m.put("required", required);
        m.put("options", options.stream().map(CalculatorOption::toMap).toList());
        m.put("help", help);
        return m;
    }
}
