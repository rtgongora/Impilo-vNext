package zw.gov.mohcc.impilo.search.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record IndexDefinitionRequest(
        @NotBlank String name,
        @NotBlank String sourceType,
        @NotNull List<FieldDefinition> fields,
        Boolean enabled
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public record FieldDefinition(
            @NotBlank String name,
            @NotBlank String type,
            boolean searchable,
            boolean filterable
    ) {}
}
