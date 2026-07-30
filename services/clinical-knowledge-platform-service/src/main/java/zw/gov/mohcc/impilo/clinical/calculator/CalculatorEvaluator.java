package zw.gov.mohcc.impilo.clinical.calculator;

/**
 * Runs one calculator over submitted inputs.
 *
 * <p>Implementations are thin: read the declared inputs, call the domain library, translate its
 * result into {@link CalculatorResult}. No arithmetic lives here. If a threshold or a coefficient
 * appears in an implementation of this interface, it belongs in {@code medicine-domain} instead,
 * where it is versioned and unit-tested against the published source.</p>
 */
@FunctionalInterface
public interface CalculatorEvaluator {

    /**
     * @param inputs the submitted values; absent keys read as null and are passed through to the
     *               calculator, which refuses in its own words rather than being defaulted here
     * @throws CalculatorInputException when a supplied value cannot be read as its declared kind
     */
    CalculatorResult evaluate(CalculatorInputs inputs);
}
