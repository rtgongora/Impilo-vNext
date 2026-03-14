package zw.gov.mohcc.impilo.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MappingTemplateRequest(

        @NotBlank @Size(max = 256)
        String name,

        @Size(max = 64)
        String sourceFormat,

        @Size(max = 64)
        String targetFormat,

        String mappingRulesJson,

        Boolean active
) {
}
