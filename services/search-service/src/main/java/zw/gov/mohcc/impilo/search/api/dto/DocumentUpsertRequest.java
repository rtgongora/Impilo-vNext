package zw.gov.mohcc.impilo.search.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record DocumentUpsertRequest(
        @NotBlank String indexId,
        @NotBlank String externalId,
        String title,
        String bodyText,
        Map<String, Object> metadata
) {}
