package zw.gov.mohcc.impilo.clinical.calculator.catalog;

import zw.gov.mohcc.impilo.clinical.calculator.CalculatorOption;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Builds a picker's options from a domain enum, so the choices a clinician sees are exactly the
 * constants the calculator accepts.
 *
 * <p>Deriving the options rather than listing them means a constant added to the domain library
 * cannot be silently missing from the form, and a constant removed cannot linger in a picker that
 * then submits a value the calculator rejects.</p>
 */
final class EnumOptions {

    private EnumOptions() {
    }

    /** Every constant of {@code type}, labelled by {@code label}. */
    static <E extends Enum<E>> List<CalculatorOption> of(Class<E> type, Function<E, String> label) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> CalculatorOption.of(constant.name(), label.apply(constant)))
                .toList();
    }

    /** Every constant of {@code type}, labelled and glossed. */
    static <E extends Enum<E>> List<CalculatorOption> of(Class<E> type, Function<E, String> label,
                                                         Function<E, String> description) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> new CalculatorOption(constant.name(), label.apply(constant),
                        description.apply(constant)))
                .toList();
    }
}
