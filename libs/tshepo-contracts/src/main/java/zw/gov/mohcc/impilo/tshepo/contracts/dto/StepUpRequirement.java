package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Structured, opaque requirement returned when a request needs stronger authentication. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepUpRequirement(
        int requiredAal,
        List<String> permittedMethods,
        Integer maxAuthAgeSeconds,
        boolean phishingResistantRequired,
        String requirementId
) {
    public StepUpRequirement {
        permittedMethods = permittedMethods == null ? List.of() : List.copyOf(permittedMethods);
    }
}
