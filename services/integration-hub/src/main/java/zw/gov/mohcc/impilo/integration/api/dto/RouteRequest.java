package zw.gov.mohcc.impilo.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RouteRequest(

        @Size(max = 128)
        String sourceService,

        @Size(max = 256)
        String eventTypePrefix,

        @Size(max = 128)
        String targetService,

        @NotBlank @Size(max = 512)
        String targetUrl,

        Boolean enabled,

        @Size(max = 16)
        String matchMethod,

        @Size(max = 512)
        String matchPathRegex,

        Map<String, String> transformHeaders,

        Map<String, String> transformFieldRenames,

        Integer targetTimeoutMs
) {
}
