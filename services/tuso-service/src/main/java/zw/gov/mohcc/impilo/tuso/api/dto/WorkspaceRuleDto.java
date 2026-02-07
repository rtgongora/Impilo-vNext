package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceRuleDto(
        @NotBlank(message = "Rule type is required")
        String ruleType,

        String actorType,
        String role,
        String privilege,
        boolean required
) {}
