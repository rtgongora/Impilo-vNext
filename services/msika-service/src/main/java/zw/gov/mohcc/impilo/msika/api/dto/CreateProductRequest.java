package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(
    @NotBlank String catalogId,
    @NotBlank String canonicalCode,
    @NotBlank String displayName,
    String description,
    String[] synonyms,
    String[] tags,
    @NotNull ProductDetailDto product
) {}
