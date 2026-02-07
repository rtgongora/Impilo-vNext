package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Obligations that downstream services MUST honour.
 *
 * <p>Injected as {@code X-Obligations} header by Envoy after TSHEPO ALLOW decision.
 * The downstream service reads these and applies field masking, scope reduction,
 * and elevated logging as required.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Obligations(
        /** Maximum data scope (e.g. "FACILITY", "WORKSPACE", "PATIENT"). */
        String maxScope,
        /** Fields to mask in response (e.g. ["name", "dob", "nid"]). */
        List<String> maskFields,
        /** Logging level override ("STANDARD", "ELEVATED", "FULL"). */
        String loggingLevel,
        /** Consent scope reference ID (for clinical resource access). */
        String consentScopeRef
) {
    public static final Obligations NONE = new Obligations(null, null, "STANDARD", null);

    public static Obligations withElevatedLogging() {
        return new Obligations(null, null, "ELEVATED", null);
    }

    public static Obligations withMasking(List<String> fields) {
        return new Obligations(null, fields, "STANDARD", null);
    }
}
