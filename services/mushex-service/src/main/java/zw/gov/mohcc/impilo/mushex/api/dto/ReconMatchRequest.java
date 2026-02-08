package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReconMatchRequest(
        @NotBlank String intentId
) {}
