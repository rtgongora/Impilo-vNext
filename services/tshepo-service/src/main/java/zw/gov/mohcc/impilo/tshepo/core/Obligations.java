package zw.gov.mohcc.impilo.tshepo.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Obligations that downstream services MUST honor after an ALLOW decision.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Obligations(
    String maxScope,
    List<String> maskFields,
    String loggingLevel,
    String consentScopeRef
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Obligations standard() {
        return new Obligations(null, null, "STANDARD", null);
    }

    public static Obligations withMasking(List<String> fields, String loggingLevel) {
        return new Obligations(null, fields, loggingLevel, null);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
