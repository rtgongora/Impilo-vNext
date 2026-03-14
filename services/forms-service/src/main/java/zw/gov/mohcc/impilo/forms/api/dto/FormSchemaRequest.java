package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormSchemaRequest(

        @NotBlank
        @Size(max = 128)
        String formKey,

        @NotBlank
        @Size(max = 256)
        String name,

        String description,

        @NotBlank
        String schemaJson
) {
}
