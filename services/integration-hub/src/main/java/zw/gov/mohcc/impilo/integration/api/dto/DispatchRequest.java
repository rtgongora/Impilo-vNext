package zw.gov.mohcc.impilo.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DispatchRequest(

        @NotBlank @Size(max = 128)
        String sourceService,

        @NotBlank @Size(max = 256)
        String eventType,

        @NotBlank @Size(max = 128)
        String targetService,

        @NotBlank
        String payloadJson
) {
}
