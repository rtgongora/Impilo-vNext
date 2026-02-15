package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVersionRequest(
        @NotBlank(message = "contentJson is required")
        String contentJson
) {
}
