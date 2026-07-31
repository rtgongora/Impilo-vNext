package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorDescriptor;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorInputException;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorRegistry;
import zw.gov.mohcc.impilo.clinical.calculator.CalculatorResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bedside clinical calculators, computed here rather than described here.
 *
 * <p>Three endpoints: the catalogue, one calculator's input schema, and its evaluation. A surface
 * renders the schema and posts values back; it does not carry a copy of the arithmetic, so there is
 * no second implementation to drift from the domain library or from the engines that consume the
 * same figures.</p>
 *
 * <h2>Status codes</h2>
 * <ul>
 *   <li><strong>200</strong> — the calculator ran. This covers a refusal: an instrument declining
 *       to score an untestable component is the instrument working, and it carries a named reason
 *       and an explanation the clinician needs to read.</li>
 *   <li><strong>400</strong> — a submitted value could not be read as the declared type. A typo is
 *       not a clinical finding and must not be laundered into a refusal.</li>
 *   <li><strong>404</strong> — no calculator is registered under that id.</li>
 * </ul>
 *
 * <p>Nothing here is persisted. A calculated figure becomes part of the record only when a clinician
 * files it through the surface that owns that observation, which is where its provenance, author and
 * time belong.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/calculators")
public class CalculatorController {

    private final CalculatorRegistry registry;

    public CalculatorController(CalculatorRegistry registry) {
        this.registry = registry;
    }

    /** Every calculator this service serves, without the input schemas. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        var summaries = registry.descriptors().stream()
                .map(CalculatorDescriptor::toSummaryMap)
                .toList();
        return ResponseEntity.ok(Map.of("data", Map.of(
                "calculators", summaries,
                "total", summaries.size())));
    }

    /** One calculator's full description, including the fields a form must render. */
    @GetMapping("/{calculatorId}")
    public ResponseEntity<Map<String, Object>> describe(@PathVariable String calculatorId) {
        Optional<CalculatorDescriptor> descriptor = registry.describe(calculatorId);
        return descriptor
                .<ResponseEntity<Map<String, Object>>>map(d -> ResponseEntity.ok(Map.of("data", d.toMap())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(calculatorId)));
    }

    /** Runs a calculator over submitted values, returning either its outputs or its refusal. */
    @PostMapping("/{calculatorId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(@PathVariable String calculatorId,
                                                        @RequestBody(required = false)
                                                        Map<String, Object> submitted) {
        Optional<CalculatorResult> result = registry.evaluate(calculatorId,
                submitted == null ? Map.of() : submitted);
        return result
                .<ResponseEntity<Map<String, Object>>>map(r -> ResponseEntity.ok(Map.of("data", r.toMap())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFound(calculatorId)));
    }

    /**
     * An unreadable value is a client error, kept distinct from a clinical refusal.
     *
     * <p>Collapsing the two would let a mistyped creatinine arrive as "this calculator declined to
     * produce a result", which reads to a clinician as a property of the patient.</p>
     */
    @ExceptionHandler(CalculatorInputException.class)
    public ResponseEntity<Map<String, Object>> onUnreadableInput(CalculatorInputException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "CALCULATOR_INPUT_INVALID");
        error.put("field", e.key());
        error.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", error));
    }

    private static Map<String, Object> notFound(String calculatorId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "CALCULATOR_NOT_FOUND");
        error.put("calculator_id", calculatorId);
        error.put("message", "No calculator is registered under '" + calculatorId + "'.");
        return Map.of("error", error);
    }
}
