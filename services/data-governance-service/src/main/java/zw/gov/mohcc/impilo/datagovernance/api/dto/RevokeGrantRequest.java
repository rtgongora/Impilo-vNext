package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RevokeGrantRequest(
        @NotNull @JsonProperty("grant_id") UUID grantId,
        @JsonProperty("revoked_by") String revokedBy
) {}
