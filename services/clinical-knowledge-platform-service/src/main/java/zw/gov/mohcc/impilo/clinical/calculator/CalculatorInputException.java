package zw.gov.mohcc.impilo.clinical.calculator;

/**
 * A submitted value could not be read as the kind the calculator declared — letters in a numeric
 * field, an unrecognised enum token, a list where a single value was expected.
 *
 * <p>This is deliberately distinct from a calculator's own refusal. A refusal is a clinical
 * statement made by the arithmetic and is returned with a 200: the calculator ran and declined to
 * produce a number, for a reason a clinician needs to read. This exception means the request was
 * malformed and never reached the arithmetic, and is returned with a 400. Collapsing the two would
 * make a typo look like a clinical finding.</p>
 */
public class CalculatorInputException extends RuntimeException {

    private final String key;

    public CalculatorInputException(String key, String message) {
        super(message);
        this.key = key;
    }

    /** The input that could not be read, so a form can mark the offending field. */
    public String key() {
        return key;
    }
}
