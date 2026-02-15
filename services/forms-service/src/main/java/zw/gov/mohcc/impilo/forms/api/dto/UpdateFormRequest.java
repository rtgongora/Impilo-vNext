package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFormRequest(
        @NotBlank(message = "name is required")
        String name,

        String description,

        String category
) {
}
