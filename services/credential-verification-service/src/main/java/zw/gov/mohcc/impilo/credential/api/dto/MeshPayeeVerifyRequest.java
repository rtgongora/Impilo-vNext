package zw.gov.mohcc.impilo.credential.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MeshPayeeVerifyRequest(
        @NotNull @JsonProperty("tenant_id") UUID tenantId,
        @NotBlank @JsonProperty("provider_id") String providerId
) {}
