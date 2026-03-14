package zw.gov.mohcc.impilo.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record CreateDefinitionRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String category,
        Map<String, Object> schema,
        @NotNull List<Map<String, Object>> steps) {}
