package zw.gov.mohcc.impilo.clinical.calculator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One permitted value of an enumerated calculator input.
 *
 * @param value       the token submitted back, matching the domain enum constant
 * @param label       the wording shown to a clinician, taken from the published instrument
 * @param description a longer gloss where the label alone would be ambiguous; null otherwise
 */
public record CalculatorOption(String value, String label, String description) {

    /** An option whose label needs no further explanation. */
    public static CalculatorOption of(String value, String label) {
        return new CalculatorOption(value, label, null);
    }

    /** Wire form, snake_case to match the rest of the CKP surface. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        m.put("description", description);
        return m;
    }
}
