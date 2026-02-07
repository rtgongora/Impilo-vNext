package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCatalogRequest(
    @NotBlank String name,
    String description,
    String scope,
    String version,
    String parentCatalogId
) {}
