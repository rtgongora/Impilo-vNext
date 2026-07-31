package zw.gov.mohcc.impilo.clinical.calculator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One named value a calculator produced.
 *
 * <p>Calculators are multi-output on purpose. BSA returns two formulae and their spread; the Wells
 * PE score returns two banding models; the Glasgow Coma Scale returns its components alongside the
 * total. Collapsing those to a single headline number is how a surface ends up choosing a
 * convention on a clinician's behalf without saying so.</p>
 *
 * @param key       the machine name, stable across content versions
 * @param label     the name as it should be displayed
 * @param value     the value — a number, a string band, or a boolean
 * @param unit      the unit, or null where the value is a classification rather than a measurement
 * @param primary   whether this is the headline value. More than one output may be primary, which
 *                  is the honest representation of an instrument that genuinely has two answers
 * @param note      a caveat attached to this specific value; null when it needs none
 */
public record CalculatorOutput(String key,
                               String label,
                               Object value,
                               String unit,
                               boolean primary,
                               String note) {

    /** A headline value with a unit. */
    public static CalculatorOutput primary(String key, String label, Object value, String unit) {
        return new CalculatorOutput(key, label, value, unit, true, null);
    }

    /** A headline value with a caveat that must travel with it. */
    public static CalculatorOutput primary(String key, String label, Object value, String unit,
                                           String note) {
        return new CalculatorOutput(key, label, value, unit, true, note);
    }

    /** A supporting value — a component score, an input echoed back for audit, a derived spread. */
    public static CalculatorOutput secondary(String key, String label, Object value, String unit) {
        return new CalculatorOutput(key, label, value, unit, false, null);
    }

    /** A supporting value with its own caveat. */
    public static CalculatorOutput secondary(String key, String label, Object value, String unit,
                                             String note) {
        return new CalculatorOutput(key, label, value, unit, false, note);
    }

    /** Wire form, snake_case to match the rest of the CKP surface. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("primary", primary);
        m.put("note", note);
        return m;
    }
}
