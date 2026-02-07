package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a new terminology pack.
 *
 * @param name         the pack name
 * @param version      the semantic version string
 * @param description  a narrative description (optional)
 * @param manifestJson optional JSON manifest describing the pack
 */
public record CreatePackRequest(
        @NotBlank String name,
        @NotBlank String version,
        String description,
        Object manifestJson
) {}
