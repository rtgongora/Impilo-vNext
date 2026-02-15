package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFormRequest(
        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "name is required")
        String name,

        String description,

        String category
) {
}
