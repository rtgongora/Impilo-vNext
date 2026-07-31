package zw.gov.mohcc.impilo.clinical.calculator;

/**
 * The shape of a calculator input, so a single generic form renderer can present every calculator
 * without a hand-written form per instrument.
 *
 * <p>The kind describes how a value is entered and parsed, not what it means clinically. Units,
 * plausibility and applicability all belong to the calculator, which refuses in its own words.</p>
 */
public enum InputKind {

    /** A number that may have a fractional part — a laboratory value, a weight, a pressure. */
    DECIMAL,

    /** A whole number — an age in years, a respiratory rate, a platelet count. */
    INTEGER,

    /**
     * A yes/no answer with a third state. Absent means "not asked", which several calculators
     * refuse on rather than reading as "no"; the renderer must therefore offer no default.
     */
    BOOLEAN,

    /** Exactly one option from {@link CalculatorInput#options()}. */
    ENUM,

    /**
     * Any number of options from {@link CalculatorInput#options()}, including none. An empty
     * selection is a real answer meaning "assessed, nothing found"; the renderer must distinguish
     * it from the input never having been touched.
     */
    ENUM_MULTI
}
