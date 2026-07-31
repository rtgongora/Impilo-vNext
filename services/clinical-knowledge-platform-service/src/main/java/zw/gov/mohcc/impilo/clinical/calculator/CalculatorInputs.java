package zw.gov.mohcc.impilo.clinical.calculator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed reads over a submitted input map.
 *
 * <p><strong>Absent is not zero and absent is not false.</strong> Every accessor returns null for a
 * value that was not supplied, and hands that null straight to the calculator, which refuses in its
 * own words. That is the whole point: the domain library has spent considerable care distinguishing
 * "not measured" from "measured and normal", and a parsing layer that defaults a missing field
 * would throw that away before the arithmetic ever saw it.</p>
 *
 * <p>A value that is present but unreadable is a different matter and raises
 * {@link CalculatorInputException}, which becomes a 400. A typo is not a clinical finding.</p>
 */
public final class CalculatorInputs {

    private final Map<String, Object> raw;

    public CalculatorInputs(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    /** True when the caller supplied the key at all, whatever its value. */
    public boolean has(String key) {
        return raw.containsKey(key) && raw.get(key) != null;
    }

    /**
     * A decimal, or null when absent.
     *
     * @throws CalculatorInputException when present but not a number
     */
    public BigDecimal decimal(String key) {
        Object value = raw.get(key);
        if (value == null || isBlank(value)) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new CalculatorInputException(key,
                    "'" + value + "' is not a number. Enter a numeric value or leave the field empty;"
                            + " an empty field is reported as not recorded rather than as zero.");
        }
    }

    /**
     * A whole number, or null when absent.
     *
     * @throws CalculatorInputException when present but not a whole number
     */
    public Integer integer(String key) {
        BigDecimal value = decimal(key);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new CalculatorInputException(key,
                    "'" + value.toPlainString() + "' must be a whole number.");
        }
    }

    /**
     * A three-valued boolean: true, false, or null when the question was not answered.
     *
     * <p>The null is load-bearing. Several calculators refuse rather than read an unanswered
     * question as a negative, because the negative reading is the one that understates severity.</p>
     *
     * @throws CalculatorInputException when present but not a boolean
     */
    public Boolean bool(String key) {
        Object value = raw.get(key);
        if (value == null || isBlank(value)) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes")) {
            return Boolean.TRUE;
        }
        if (text.equals("false") || text.equals("no")) {
            return Boolean.FALSE;
        }
        throw new CalculatorInputException(key,
                "'" + value + "' is not a yes/no answer. Leave the field unanswered if it was not"
                        + " assessed — that is recorded as not assessed, which several calculators"
                        + " treat differently from 'no'.");
    }

    /**
     * A single enum constant, or null when absent.
     *
     * @throws CalculatorInputException when present but not a constant of {@code type}
     */
    public <E extends Enum<E>> E enumValue(String key, Class<E> type) {
        Object value = raw.get(key);
        if (value == null || isBlank(value)) {
            return null;
        }
        return parseConstant(key, type, value.toString().trim());
    }

    /**
     * A set of enum constants.
     *
     * <p>Returns an empty set when the caller supplied an empty list, and null when the key was
     * absent altogether. Those mean different things — "assessed, nothing found" against "not
     * assessed" — and the calculators act on the difference.</p>
     *
     * @throws CalculatorInputException when present but not a list of constants of {@code type}
     */
    public <E extends Enum<E>> Set<E> enumSet(String key, Class<E> type) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new CalculatorInputException(key,
                    "Expected a list of values but received '" + value + "'.");
        }
        Set<E> result = EnumSet.noneOf(type);
        for (Object element : collection) {
            if (element == null || isBlank(element)) {
                continue;
            }
            result.add(parseConstant(key, type, element.toString().trim()));
        }
        return result;
    }

    private <E extends Enum<E>> E parseConstant(String key, Class<E> type, String token) {
        String normalised = token.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalised)) {
                return constant;
            }
        }
        Set<String> permitted = new LinkedHashSet<>();
        for (E constant : type.getEnumConstants()) {
            permitted.add(constant.name());
        }
        throw new CalculatorInputException(key,
                "'" + token + "' is not one of the permitted values " + permitted + ".");
    }

    private static boolean isBlank(Object value) {
        return value instanceof String s && s.isBlank();
    }
}
