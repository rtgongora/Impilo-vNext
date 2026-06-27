package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO to flag a result as critical, with a mandatory clinical reason. */
public record CriticalFlagRequest(@NotBlank String reason) {}
