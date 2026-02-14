package zw.gov.mohcc.impilo.rules.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RuleRequest(
        @NotBlank String name,
        @NotBlank String expression,
        Boolean enabled
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
