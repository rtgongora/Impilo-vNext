package zw.gov.mohcc.impilo.clinical.calculator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a calculator produced, or the named reason it produced nothing.
 *
 * <p>There are exactly two shapes and a surface must render them differently. A computed result has
 * outputs and no refusal. A refusal has a reason, a clinician-readable explanation, and no outputs
 * — and it is not an error: refusing to score an intubated patient's verbal GCS is the calculator
 * working correctly. A third state, the request never reaching the service, belongs to the caller
 * and is not represented here.</p>
 *
 * @param calculatorId   which calculator ran
 * @param computed       true when {@code outputs} carries values
 * @param outputs        the named values; empty when refused
 * @param refusalReason  the machine-readable code from the domain library; null when computed
 * @param refusalDetail  the clinician-readable explanation; null when computed. This is the text a
 *                       surface must show — it says what to do next, which a bare code does not
 * @param caveats        standing warnings that apply whether or not the calculation succeeded, such
 *                       as an instrument's known poor performance in a population. A surface that
 *                       drops these is misrepresenting the result
 * @param contentVersion the domain library's content version, so a stored result can be re-read
 *                       against the arithmetic that produced it
 */
public record CalculatorResult(String calculatorId,
                               boolean computed,
                               List<CalculatorOutput> outputs,
                               String refusalReason,
                               String refusalDetail,
                               List<String> caveats,
                               String contentVersion) {

    /** A successful computation. */
    public static CalculatorResult of(String calculatorId, String contentVersion,
                                      List<String> caveats, List<CalculatorOutput> outputs) {
        return new CalculatorResult(calculatorId, true, List.copyOf(outputs), null, null,
                List.copyOf(caveats), contentVersion);
    }

    /**
     * A refusal. {@code reason} is the domain {@code RefusalReason} name and {@code detail} its
     * explanation; both come from the calculator rather than being written here, so the wording a
     * clinician reads is the wording the arithmetic authored.
     */
    public static CalculatorResult refused(String calculatorId, String contentVersion,
                                           List<String> caveats, String reason, String detail) {
        return new CalculatorResult(calculatorId, false, List.of(), reason, detail,
                List.copyOf(caveats), contentVersion);
    }

    /** Wire form, snake_case to match the rest of the CKP surface. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calculator_id", calculatorId);
        m.put("computed", computed);
        m.put("outputs", outputs.stream().map(CalculatorOutput::toMap).toList());
        m.put("refusal_reason", refusalReason);
        m.put("refusal_detail", refusalDetail);
        m.put("caveats", caveats);
        m.put("content_version", contentVersion);
        return m;
    }
}
