package zw.gov.mohcc.impilo.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record NotifyRequest(

        @Size(max = 128)
        String templateKey,

        @NotBlank
        @Size(max = 32)
        String channel,

        @NotBlank
        @Size(max = 256)
        @JsonProperty("to")
        String recipient,

        Map<String, String> variables
) {
}
