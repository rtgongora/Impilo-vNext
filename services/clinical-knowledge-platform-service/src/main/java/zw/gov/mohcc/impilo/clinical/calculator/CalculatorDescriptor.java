package zw.gov.mohcc.impilo.clinical.calculator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a surface needs to present one calculator: what it is, what it needs, where its
 * numbers come from, and what must be said alongside its answer.
 *
 * <p>Descriptors exist so there is one generic form renderer rather than one hand-written form per
 * instrument. A hand-written form drifts from the calculator behind it — a field is renamed, a unit
 * changes, a new input is added — and the drift is invisible until someone gets a wrong number.
 * Here the fields are declared next to the arithmetic that consumes them.</p>
 *
 * @param id             stable machine identifier, used in the URL and in stored results
 * @param name           the instrument's published name
 * @param category       the clinical grouping a surface may organise by
 * @param summary        one or two sentences on what the calculator is for
 * @param sourceCitation the published source the arithmetic was transcribed from. Every calculator
 *                       names one; none of the primary texts are vendored in this repository, and
 *                       saying so is part of the answer
 * @param inputs         the fields, in the order they should be presented
 * @param caveats        standing warnings that must be shown with any result. These are properties
 *                       of the instrument, not of a particular patient
 * @param contentVersion the domain library's content version for this calculator
 */
public record CalculatorDescriptor(String id,
                                   String name,
                                   String category,
                                   String summary,
                                   String sourceCitation,
                                   List<CalculatorInput> inputs,
                                   List<String> caveats,
                                   String contentVersion) {

    public CalculatorDescriptor {
        inputs = List.copyOf(inputs);
        caveats = List.copyOf(caveats);
    }

    /**
     * A refusal carrying this calculator's identity, version and standing caveats.
     *
     * <p>The caveats travel with a refusal as well as with a result. An instrument's known
     * limitations do not stop applying because it declined to produce a number.</p>
     *
     * @param reason the domain {@code RefusalReason} name
     * @param detail the calculator's own explanation, which is the text a clinician reads
     */
    public CalculatorResult refuse(String reason, String detail) {
        return CalculatorResult.refused(id, contentVersion, caveats, reason, detail);
    }

    /** A computed result carrying this calculator's identity and standing caveats. */
    public CalculatorResult produce(String contentVersion, List<CalculatorOutput> outputs) {
        return CalculatorResult.of(id, contentVersion, caveats, outputs);
    }

    /** Wire form, snake_case to match the rest of the CKP surface. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("summary", summary);
        m.put("source_citation", sourceCitation);
        m.put("inputs", inputs.stream().map(CalculatorInput::toMap).toList());
        m.put("caveats", caveats);
        m.put("content_version", contentVersion);
        return m;
    }

    /** The listing form, without the input schema, for an index that only needs to name them. */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("category", category);
        m.put("summary", summary);
        m.put("content_version", contentVersion);
        return m;
    }
}
