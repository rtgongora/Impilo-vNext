package zw.gov.mohcc.impilo.indawo.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record UpsertSiteRequest(
        @NotBlank String name,
        @NotBlank String type,
        Map<String, Object> address,
        Map<String, Object> geo,
        String status
) {}
