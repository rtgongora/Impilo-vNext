package zw.gov.mohcc.impilo.credential.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SupersedeCredentialRequest(
        @NotNull(message = "newCredentialId is required")
        UUID newCredentialId
) {}
