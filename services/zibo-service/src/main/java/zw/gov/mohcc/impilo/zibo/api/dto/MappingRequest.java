package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for mapping a code from one system to another.
 *
 * @param sourceSystem the source coding system URI
 * @param sourceCode   the source code to map
 * @param targetSystem the target coding system URI
 */
public record MappingRequest(
        @NotBlank String sourceSystem,
        @NotBlank String sourceCode,
        @NotBlank String targetSystem
) {}
