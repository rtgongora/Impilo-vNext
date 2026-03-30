package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCodeSystemRequest(
        @NotBlank String canonicalUrl,
        @NotBlank String version,
        @NotBlank String name,
        String title,
        String description,
        @NotNull Object contentJson,
        String publisher
) {}
