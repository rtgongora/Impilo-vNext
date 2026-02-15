package zw.gov.mohcc.impilo.rules.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuleRequest(
        @NotBlank @Size(max = 128) String key,
        @Size(max = 256) String name
) {
}
