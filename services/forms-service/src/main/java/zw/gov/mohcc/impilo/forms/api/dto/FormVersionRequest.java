package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FormVersionRequest(

        @NotBlank
        String schemaJson,

        String changelog
) {
}
