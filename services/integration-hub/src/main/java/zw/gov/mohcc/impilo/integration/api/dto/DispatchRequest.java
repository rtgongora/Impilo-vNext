package zw.gov.mohcc.impilo.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record DispatchRequest(

        @NotBlank @Size(max = 16)
        String method,

        @NotBlank @Size(max = 512)
        String path,

        String body,

        Map<String, String> headers
) {
}
