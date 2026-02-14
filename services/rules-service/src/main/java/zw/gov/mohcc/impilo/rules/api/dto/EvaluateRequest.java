package zw.gov.mohcc.impilo.rules.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record EvaluateRequest(
        @NotNull Map<String, Object> facts
) {
}
