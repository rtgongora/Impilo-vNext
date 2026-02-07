package zw.gov.mohcc.impilo.credential.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeCredentialRequest(
        @NotBlank(message = "reason is required")
        String reason
) {}
