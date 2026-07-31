package zw.gov.mohcc.impilo.clinical.calculator;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.clinical.calculator.catalog.CardiovascularGeriatricCalculators;
import zw.gov.mohcc.impilo.clinical.calculator.catalog.OrganSystemCalculators;
import zw.gov.mohcc.impilo.clinical.calculator.catalog.RenalMetabolicCalculators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The calculators this service serves, each bound to the arithmetic behind it.
 *
 * <p>Registration binds a descriptor to an evaluator in one object, so a calculator cannot be
 * advertised without an implementation and cannot be implemented without being described. That was
 * the failure being corrected here: a surface listed instruments it could not compute, and the
 * listing was the only thing anyone checked.</p>
 *
 * <p>The arithmetic itself lives in {@code libs/medicine-domain} and is not duplicated here. This
 * class knows how to ask a question and how to present an answer; it does not know how to work one
 * out. That separation is what lets the same eGFR feed a clinician's form, the multimorbidity
 * engine's renal panel and a deprescribing gate without three implementations drifting apart.</p>
 */
@Service
public class CalculatorRegistry {

    private final Map<String, CalculatorRegistration> byId;

    public CalculatorRegistry() {
        List<CalculatorRegistration> all = new ArrayList<>();
        all.addAll(RenalMetabolicCalculators.registrations());
        all.addAll(OrganSystemCalculators.registrations());
        all.addAll(CardiovascularGeriatricCalculators.registrations());

        Map<String, CalculatorRegistration> index = new LinkedHashMap<>();
        for (CalculatorRegistration registration : all) {
            CalculatorRegistration clash = index.put(registration.descriptor().id(), registration);
            if (clash != null) {
                throw new IllegalStateException("Two calculators claim the id '"
                        + registration.descriptor().id() + "'. Ids appear in stored results, so a"
                        + " collision would make a recorded value unreadable.");
            }
        }
        this.byId = Map.copyOf(index);
    }

    /** Every calculator, in catalogue order. */
    public List<CalculatorDescriptor> descriptors() {
        return byId.values().stream().map(CalculatorRegistration::descriptor).toList();
    }

    /** One calculator's description, or empty when nothing is registered under that id. */
    public Optional<CalculatorDescriptor> describe(String id) {
        return Optional.ofNullable(byId.get(id)).map(CalculatorRegistration::descriptor);
    }

    /** True when {@code id} is served. */
    public boolean isRegistered(String id) {
        return byId.containsKey(id);
    }

    /**
     * Runs a calculator over submitted values.
     *
     * <p>Returns empty only when the id is unknown. A calculator that declines to produce a number
     * returns a refusal, which is a successful evaluation of a well-formed request, not a
     * failure.</p>
     *
     * @throws CalculatorInputException when a supplied value cannot be read as the type declared
     */
    public Optional<CalculatorResult> evaluate(String id, Map<String, Object> submitted) {
        CalculatorRegistration registration = byId.get(id);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(registration.evaluator().evaluate(new CalculatorInputs(submitted)));
    }
}
