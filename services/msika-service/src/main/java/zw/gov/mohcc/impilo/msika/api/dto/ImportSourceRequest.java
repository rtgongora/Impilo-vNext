package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ImportSourceRequest(
    @NotBlank String name,
    @NotBlank String mode,
    Object config
) {}
