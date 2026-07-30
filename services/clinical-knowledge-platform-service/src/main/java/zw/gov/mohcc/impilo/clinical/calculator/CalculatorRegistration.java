package zw.gov.mohcc.impilo.clinical.calculator;

/**
 * A calculator's description bound to the code that runs it.
 *
 * <p>The pairing is the point. A descriptor without an evaluator is an advertised calculator that
 * returns nothing when a clinician uses it — which is precisely the defect this whole surface
 * exists to correct — and an evaluator without a descriptor is unreachable. Registering them
 * together makes both failure modes unrepresentable, and a guard test asserts the catalogue is
 * consistent in both directions.</p>
 */
public record CalculatorRegistration(CalculatorDescriptor descriptor, CalculatorEvaluator evaluator) {

    /** The identifier this calculator is addressed by. */
    public String id() {
        return descriptor.id();
    }
}
