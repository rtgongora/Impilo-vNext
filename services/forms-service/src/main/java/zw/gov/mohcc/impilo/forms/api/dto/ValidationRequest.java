package zw.gov.mohcc.impilo.forms.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidationRequest(

        @NotBlank
        String payload
) {
}
